(function() {
var Plugin = function () {
};
Plugin.prototype = new PluginStub();

Plugin.prototype.onStart = function () {
    log('Dev info plugin started:', this.radio, this.device, this.id);
};

Plugin.prototype.onStop = function() {
    log('Dev info plugin stopped');
};
Plugin.prototype.onRender = function(div) {
    log('Dev info plugin render', div);
};
Plugin.prototype.onShow = function() {
    log('Dev info plugin show');
};
Plugin.prototype.onHide = function() {
    log('Dev info plugin hide');
};
Plugin.prototype.onClose = function() {
    log('Dev info plugin closed');
};

Plugin.pluginName = 'devinfo';
Plugin.pluginDescription = 'Device Info';

registerPlugin(Plugin);
}).call();