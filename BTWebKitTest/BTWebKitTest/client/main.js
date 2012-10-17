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
    this.loadPlugins();
    log('OK...', window.bt, window.bt.isSupported());
    window.bt.enumerateDevices(this.h(function(err, data) {
        if (err) {
            return this.showError(err);
        }
        for (var i = 0; i<data.radios.length; i++) {
            this.radios = data.radios;
            window.bt.listen(data.radios[i].address, this.uuid,
                this.h(function(err) {
                    log('Listen result:', err);
                }.bind(this)),
                this.h(function(err, remote, data) {
                    log('Data? result:', err, data);
                    return 3;
                }.bind(this)));
        };
        log('enumerateDevices', err, data.radios);
        return null;
    }.bind(this)));
};

App.prototype.showError = function (message) {
    alert(message);
}

App.prototype.loadPlugins = function () {
    this.plugins = [];
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
        yepnope({load: urls});
        return null;
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
    this.addButton = $('#add-pair');
    this.addButton.on('click', function(){
        this.testBT('BCB1F37DC74F');
    }.bind(this));
};

App.prototype.h = function(fn) {
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