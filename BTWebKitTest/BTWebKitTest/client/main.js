yepnope({
    load: ['lib/jquery-1.8.2.min.js', 'bs/css/bootstrap.min.css', 'bs/js/bootstrap.min.js', 'lib/date.js', 'lib/cross-utils.js', 'res/main.css'],
    complete: function () {
        $(document).ready(function() {
            new App();
        });
    }
});

var App = function() {
    this.fnIndex = 0;
    this.createUI();
    this.loadPlugins();
    log('OK...', window.bt, window.bt.isSupported());
    window.bt.enumerateDevices(this.h(function(err, data) {
        log('enumerateDevices', err, data.radios);
    }));
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

App.prototype.createUI = function() {
    var nbar = $(document.createElement('div')).addClass('navbar').appendTo(document.body);
    var nbarinner = $(document.createElement('div')).addClass('navbar-inner').appendTo(nbar);
    var title = $(document.createElement('a')).addClass('brand').attr('href', '#').text('Bluetooth').appendTo(nbarinner);

    this.root = $(document.createElement('div')).addClass('container-fluid root').appendTo(document.body);
    var row = $(document.createElement('div')).addClass('row-fluid').appendTo(this.root);
    this.left = $(document.createElement('div')).addClass('span3').appendTo(row);
    this.right = $(document.createElement('div')).addClass('span9').appendTo(row);
    this.addButton = $(document.createElement('button')).addClass('btn btn-primary').text('New item').appendTo(this.left);
};

App.prototype.h = function(fn) {
    var fnName = window.events.method()+(this.fnIndex++);
    window[fnName] = function(remove, err, data) {
        if (remove && window[fnName]) {
            delete window[fnName];
        };
        fn(err, data);
    }
    return fnName;
};