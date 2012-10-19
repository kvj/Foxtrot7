(function() {
var Plugin = function () {
}

Plugin.prototype = new PluginStub();

Plugin.pluginName = 'messages';
Plugin.pluginDescription = 'Messaging';

registerPlugin(Plugin);
}).call();