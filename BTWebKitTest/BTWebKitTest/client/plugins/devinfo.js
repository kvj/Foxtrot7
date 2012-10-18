(function() {
var Plugin = function () {
};
Plugin.prototype = PluginStub.prototype;

Plugin.pluginName = 'devinfo';
Plugin.pluginDescription = 'Device Info';

registerPlugin(Plugin);
}).call();