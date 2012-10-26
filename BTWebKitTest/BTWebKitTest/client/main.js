yepnope({
    load: ['lib/jquery-1.8.2.min.js', 'bs/css/bootstrap.min.css', 'bs/js/bootstrap.min.js', 'lib/date.js', 'lib/cross-utils.js', 'res/main.css'],
    complete: function () {
        $(document).ready(function() {
            new App();
        });
    }
});

var App = function() {
    this.uuid = '{EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DC}';
    this.fnIndex = 0;
    this.createUI();
    this.showAlert('Starting...');
    this.initDB(function(err) {
        if (err) {
            return this.showError(err);
        }
        return this.initDevices();
    }.bind(this));
    log('OK...', window.bt, window.bt.isSupported());
};

App.prototype.initDevices = function () {
    window.bt.enumerateDevices(this.h(function(err, data) {
        if (err) {
            return this.showError(err);
        }
        this.radios = data.radios;
        this.loadPlugins();
        for (var i = 0; i<data.radios.length; i++) {
            window.bt.listen(data.radios[i].address, this.uuid,
                this.h(function(err) {
                    log('Listen result:', err);
                }.bind(this)),
                this.h(function(err, remote, data) {
                    log('Data? result:', err, data);
                    try {
                        this.onMessage(remote, data);
                        return 0;
                    } catch (e) {
                        log('Error parsing data:', e);
                        return 4;
                    }
                }.bind(this)));
        };
        log('enumerateDevices', err, data.radios);
        return null;
    }.bind(this)));
};

App.prototype.onMessage = function (device, data) {
    var ctx = {};
    ctx.id = data.id;
    ctx.from = data.from;
    ctx.device = device;
    ctx.inResponse = data.response;
    ctx.serie = data.serie;
    log('onMessage', device, data, ctx);
    if (ctx.inResponse && this.waitForResponse[ctx.inResponse]) {
        log('Direct response - ignore handlers');
        this.waitForResponse[ctx.inResponse](null, data.data, ctx);
        delete this.waitForResponse[ctx.inResponse];
        return;
    }
    for (var id in this.running) {
        var r = this.running[id];
//        log('Message handler', r.device, device, data.data, id);
        if (r.device == device) {
            if (r.onMessage(data.data, ctx) == true) {
                return;
            };
        };
    };
};

App.prototype.showAlert = function (message, title, config) {
    if (!config) {
        config = {};
    }
    var div = $(document.createElement('div')).appendTo($('#alerts')).addClass('alert alert-'+(config.severity || 'info'));
    $(document.createElement('button')).appendTo(div).addClass('close').attr({'data-dismiss': 'alert'}).html('&times;');
    $(document.createElement('h4')).appendTo(div).text(title || 'Untitled');
    $(document.createElement('span')).appendTo(div).text(message);
    if (!config.persistent) {
        setTimeout(function() {
            div.remove();
        }.bind(this), 5000);
    }
};

App.prototype.showError = function (message) {
    this.showAlert(message, 'Error!', {severity: 'error', persistent: true});
}

App.prototype.initDB = function (handler) {
    return handler(null); // Use of localStorage
/*
    var targetVersion = 1;
    var indexedDB = window.indexedDB || window.webkitIndexedDB;
    if (!indexedDB) {
        return handler('No indexed DB exist');
    }
    var request = indexedDB.open("Foxtrot7", targetVersion);
    log('Request:', request);
    request.onerror = function(evt) {
        log('DB error:', evt);
        this.showError('DB error: '+evt.target.errorCode);
    }.bind(this);
    var upgrade = function(version, transaction) {
        var db = transaction.db;
        log('Upgrade from', version, 'to', targetVersion, transaction, db.version);
        for (var i = version+1; i<=targetVersion; i++) {
            switch(i) {
                case 1:
                    db.createObjectStore('pairs', {keyPath: 'id', autoIncrement: true});
                    break;
            }
        }
    }.bind(this);
    request.onsuccess = function (evt) {
        var db = request.result;
        log('Success:', evt, db.version, targetVersion, db);
        var version = parseInt(db.version || 0, 10);
        if (version != targetVersion) {
            var versionRequest = db.setVersion(targetVersion);
            versionRequest.onsuccess = function (e) {
                var transaction = versionRequest.result;
                upgrade(version, transaction);
            };
        }
        this.db = db;
        handler(null);
    }.bind(this);
    request.onupgradeneeded = function (evt) {
        var transaction = request.transaction;
        upgrade(evt.oldVersion, transaction);
    }.bind(this);
    return null;
*/
};

App.prototype._get = function(plugin, name, handler) {
    var value = localStorage[''+plugin+'_'+name];
    if (handler) {
        handler(null, value);
    }
    return value;
};

App.prototype._set = function(plugin, name, value, handler) {
    localStorage[''+plugin+'_'+name] = value;
    if (handler) {
        handler(null, value);
    }
};

App.prototype._list = function(plugin, storage, handler) {
    try {
        var pairs = JSON.parse(this._get(plugin, storage) || '[]');
        handler(null, pairs);
        return pairs;
    } catch (e) {
        log('JSON error:', e);
        handler('Error: '+e);
        return null;
    }
};

App.prototype._save = function(plugin, storage, obj, handler) {
    try {
        var arr = JSON.parse(this._get(plugin, storage) || '[]');
        var needAppend = true;
        if (obj.id) {
            for (var i = 0; i<arr.length; i++) {
                if (arr[i].id == obj.id) {
                    arr.splice(i, 1, obj);
                    needAppend = false;
                    break;
                }
            }
        }
        if (needAppend) {
            if (!obj.id) {
                obj.id = new Date().getTime();
            }
            arr.push(obj);
        }
        this._set(plugin, storage, JSON.stringify(arr));
        handler(null, obj);
        return obj;
    } catch (e) {
        handler('Error');
        return null;
    }
}

App.prototype._remove = function(plugin, storage, obj, handler) {
    try {
        var arr = JSON.parse(this._get(plugin, storage) || '[]');
        var needSave = false;
        for (var i = 0; i<arr.length; i++) {
            if (arr[i].id == obj.id) {
                arr.splice(i, 1);
                i--;
                needSave = true;
            }
        }
        if (needSave) {
            this._set(plugin, storage, JSON.stringify(arr));
        }
        handler(null, needSave);
        return needSave;
    } catch (e) {
        handler('Error');
        return null;
    }
}

App.prototype.addPair = function(obj, handler) {
    return this._save('', 'pairs', obj, handler);
};

App.prototype.removePair = function(obj, handler) {
    return this._remove('', 'pairs', obj, handler);
};

App.prototype.getPairs = function(handler) {
    return this._list('', 'pairs', handler);
};

App.prototype.setProgress = function(instance, type, value) {
    window.bt.setProgress(type || 'no', value || 0);
};

App.prototype.raise = function(instance) {
    window.bt.raise();
};

App.prototype.showBallon = function(instance, message, messageType, handler) {
    window.bt.showBallon('Foxtrot7', message, messageType, this.h(handler));
};

App.prototype.openTab = function(r, d, p) {
    var tabsContentDiv = $('#main-tabs-content');
    var listDiv = $('#pairs-list');
    var tabDiv = tabsContentDiv.children('#body'+p.pair.id);
    var closeTab = function() {
        this.notifyPlugin(p.pair.id, 'onHide');
        this.notifyPlugin(p.pair.id, 'onClose');
        tabDiv.remove();
        listDiv.children('#list'+p.pair.id).removeClass('active');
    }.bind(this);
    var removeLink = function () {
        this.removePair(p.pair, function(err) {
            if (err) {
                return this.showError(err);
            }
            closeTab();
            this.notifyPlugin(p.pair.id, 'onStop');
            this.reloadAddPairMenu();
            return null;
        }.bind(this));
    }.bind(this);
    if (tabDiv.size() == 0) {
        // Create tab
        tabDiv = $('#tab-template').clone().attr('id', 'body'+p.pair.id).appendTo(tabsContentDiv);
        tabDiv.find('.plugin-caption').text(p.caption);
        tabDiv.find('.plugin-close').on('click', closeTab);
        tabDiv.find('.plugin-remove').on('click', removeLink);
        this.notifyPlugin(p.pair.id, 'onRender', tabDiv);
    }
    // Check current selected
    var sel = listDiv.children('.active');
    if (sel.size()>0) {
        if (sel.attr('id') == 'list'+p.pair.id) {
            // Already selected
            return;
        }
        this.notifyPlugin(sel.attr('data-pair'), 'onHide');
        sel.removeClass('active');
    }
    tabsContentDiv.children().removeClass('active');
    tabDiv.addClass('active');
    listDiv.children('#list'+p.pair.id).addClass('active');
    this.notifyPlugin(p.pair.id, 'onShow');
};

App.prototype.reloadAddPairMenu = function(handler) {
    var findInArray = function(arr) {
        var pairs = [];
        for (var i = 1; i<arguments.length-1; i+=2) {
            pairs.push({field: arguments[i], value: arguments[i+1]});
        }
        for (var i = 0; i<arr.length; i++) {
            var ok = true;
            if(arr[i]) {
                for (var j = 0; j<pairs.length; j++) {
                    if (arr[i][pairs[j].field] != pairs[j].value) {
                        ok = false;
                        break;
                    }
                }
                if (ok)
                    return arr[i];
            }
        }
        return null;
    };
    var buildPairTree = function(pairs) {
        var result = [];// Radio
        for (var i = 0; i<pairs.length; i++) {
            var p = pairs[i];
            if (findInArray(result, 'address', p.radio)) {
                // Already added, skip
                continue;
            }
            var r = findInArray(this.radios, 'address', p.radio);
            var obj = {address: p.radio, name: p.radio, devices: []};
            if (r) { // Append name
                obj.radio = r;
                obj.name = r.name;
            }
            result.push(obj);
            for (var j = 0; j<pairs.length; j++) {
                p = pairs[j];
                if (p.radio != obj.address) {
                    continue;
                }
                var dev = findInArray(obj.devices, 'address', p.device);
                if (!dev) {
                    var d = r != null? findInArray(r.devices, 'address', p.device): null;
                    dev = {address: p.device, name: p.device, plugins: []};
                    if (d) {
                        dev.name = d.name;
                        dev.device = d;
                    }
                    obj.devices.push(dev);
                }
                var pl = {name: p.plugin, caption: p.plugin};
                var plugin = findInArray(this.plugins, 'pluginName', pl.name);
                if (plugin) {
                    pl.caption = plugin.pluginDescription;
                    pl.plugin = plugin;
                }
                pl.pair = p;
                dev.plugins.push(pl);
            }
        }
        return result;
    }.bind(this);
    var pairItem = function(r, d, p) {
//        log('Pair item', r, d, p);
        var li = $(document.createElement('li')).attr('id', 'list'+p.pair.id);
        li.attr('data-pair', p.pair.id);
        var a = $(document.createElement('a')).attr('href', '#').text(p.caption).appendTo(li);
        a.on('click', function(evt) {
            this.openTab(r, d, p);
        }.bind(this));
        return li;
    }.bind(this);
    var newPair = function(r, p, d) {
        var li = $(document.createElement('li'));
        var a = $(document.createElement('a')).attr('href', '#').text(d.name).appendTo(li);
        a.on('click', function(evt) {
            this.addPair({radio: r.address, plugin: p.pluginName, device: d.address}, function(err, obj) {
                log('Add result:', err, obj);
                if (err) {
                    return this.showError(err);
                }
                this.startPlugin(r.address, d.address, p, obj);
                return this.reloadAddPairMenu();
            }.bind(this));
        }.bind(this));
        return li;
    }.bind(this);
    var showPairs = function(pairs){
        var tree = buildPairTree(pairs);
//        log('Tree', tree);
        var root = $('#add-pair-menu');
        root.empty();
//        log('reloadAddPairMenu', this.radios);
        for (var i = 0; i<this.radios.length; i++) {
            var r = this.radios[i];
            var li = $(document.createElement('li')).addClass('dropdown-submenu').appendTo(root);
            $(document.createElement('a')).attr('href', '#').text(r.name).appendTo(li);
            var ul = $(document.createElement('ul')).addClass('dropdown-menu').appendTo(li);
            for (var j = 0; j<this.plugins.length; j++) {
                var p = this.plugins[j];
//                log('Plugin', p);
                var pli = $(document.createElement('li')).addClass('dropdown-submenu').appendTo(ul);
                $(document.createElement('a')).attr('href', '#').text(p.pluginDescription).appendTo(pli);
                var pul = $(document.createElement('ul')).addClass('dropdown-menu').appendTo(pli);
                for (var k = 0; k<r.devices.length; k++) {
                    var d = r.devices[k];
//                    log('Device', d);
                    if (findInArray(pairs, 'radio', r.address, 'device', d.address, 'plugin', p.pluginName)){
                        continue; // Skip, already added
                    }
                    pul.append(newPair(r, p, d));
                }
            }
        }
        var list = $('#pairs-list').empty();
        for (var i = 0; i<tree.length; i++) {
            var r = tree[i];
            for (var j = 0; j<r.devices.length; j++) {
                var d = r.devices[j];
                var li = $(document.createElement('li')).addClass('nav-header').appendTo(list);
                li.text(r.name+' - '+d.name);
                for (var k = 0; k<d.plugins.length; k++) {
                    var p = d.plugins[k];
                    list.append(pairItem(r, d, p));
                    if (handler) {
                        handler(r, d, p);
                    }
                };
            };
        };
    }.bind(this);
    this.getPairs(function(err, pairs) {
        if (err) {
            return this.showError(err);
        }
        showPairs(pairs);
        return null;
    }.bind(this));
}

var PluginStub = function () {};
PluginStub.prototype.init = function(app, radio, device){
    this.app = app;
    this.radio = radio;
    this.device = device;
};
PluginStub.prototype.onStart = function() {};
PluginStub.prototype.onStop = function() {};
PluginStub.prototype.onRender = function(div) {};
PluginStub.prototype.onShow = function() {};
PluginStub.prototype.onHide = function() {};
PluginStub.prototype.onClose = function() {};
PluginStub.prototype.onMessage = function(message, ctx) {};
PluginStub.prototype.send = function(message, ctx) {
    if (!ctx) {
        ctx = {};
    }
    return this.app.send(this, message, ctx);
};

App.prototype.notifyPlugin = function (id, type) {
    if (!id || !this.running[id]) {
        this.showError('Plugin is not running');
        return null;
    }
    var instance = this.running[id];
    var args = [];
    if (!instance[type] || !instance[type].apply) {
        log('Error calling', type);
        this.showError('Plugin error');
        return null;
    }
    for (var i = 2; i<arguments.length; i++) {
        args.push(arguments[i]);
    }
    return instance[type].apply(instance, args);
}

App.prototype.startPlugin = function (radio, device, plugin, pair) {
    var instance = new plugin();
    instance.init(this, radio, device);
    instance.id = pair.id;
    instance.plugin = plugin.pluginName;
    this.running[pair.id] = instance;
    instance.onStart();
}

App.prototype.loadPlugins = function () {
    this.plugins = [];
    this.running = {};
    this.waitForResponse = {};
    window.registerPlugin = function(plugin) {
        log('Plugin loaded:', plugin);
        this.plugins.push(plugin);
    }.bind(this);
    bt.enumeratePlugins(this.h(function(err, data) {
        if (err) {
            return this.showError(err);
        }
        var urls = [];
        for (var i = 0; i<data.plugins.length; i++) {
            urls.push('plugins/'+data.plugins[i].file);
        }
        yepnope({load: urls, complete: function(){
            log('All plugins loaded:', this.plugins.length);
            this.reloadAddPairMenu(function (r, d, p) {
                this.startPlugin(r.address, d.address, p.plugin, p.pair);
            }.bind(this));
        }.bind(this)});
        return null;
    }.bind(this)));
}

App.prototype.send = function(running, message, ctx) {
    var packet = {
        from: running.plugin,
        id: new Date().getTime(),
        data: message
    };
    if (ctx.inResponse) {
        packet.response = ctx.inResponse;
    }
    if (ctx.serie) {
        packet.serie = ctx.serie;
    };
    log('App.send:', running.radio, running.device, packet);
    if (ctx.onResponse) {
        this.waitForResponse[packet.id] = ctx.onResponse;
        // TODO: Add timeout
    };
    window.bt.send(running.radio, this.uuid, running.device, JSON.stringify(packet), this.h(function(err) {
        log('Send result:', err);
        if (ctx.handler) {
            ctx.handler(err);
        };
    }.bind(this)));
}

App.prototype.testBT = function(addr) {
    var packet = {
        from: 'plugin0',
        data: ''
    };
    window.bt.send(this.radios[0].address, this.uuid, addr, JSON.stringify(packet), this.h(function(err) {
        log('Send result:', err);
    }.bind(this)));
};

App.prototype.createUI = function() {
    this.root = $('#root');
    this.left = $('#left-col');
    this.right = $('#right-col');
//    this.addButton = $('#add-pair');
//    this.addButton.on('click', function(){
//        this.testBT('BCB1F37DC74F');
//    }.bind(this));
};

App.prototype.h = function(fn) {
    if (!fn) {
        return null;
    }
    var fnName = window.events.method()+(this.fnIndex++);
    window[fnName] = function(remove) {
        if (remove && window[fnName]) {
            delete window[fnName];
        };
        var args = [];
        for (var i = 1; i<arguments.length; i++) {
            args.push(arguments[i]);
        }
        return fn.apply(this, args);
    }
    return fnName;
};