(function() {
var Plugin = function () {
}

Plugin.prototype = PluginStub.prototype;

Plugin.pluginName = 'messages';
Plugin.pluginDescription = 'Messaging';

registerPlugin(Plugin);
}).call();