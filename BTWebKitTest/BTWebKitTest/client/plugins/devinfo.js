(function() {
var Plugin = function () {
};
Plugin.prototype = new PluginStub();

var html = '<div><div class="progress b_progress"><div class="bar b_bar"></div></div><dl class="dl-horizontal d_info"></dl></div>';

Plugin.prototype.onStart = function () {
    log('Dev info plugin started:', this.radio, this.device, this.id);
};

Plugin.prototype.onStop = function() {
    log('Dev info plugin stopped');
};

Plugin.prototype.startRefresh = function () {
    this.progressBar.css({
        width: '0%'
    })
    this.send({type: 'get'}, {
        onResponse: function(err, message, ctx) {
            log('Got direct response', message);
            if (err) {
                return this.showError(err);
            }
            this.refresh(message);
            return null;
        }.bind(this)
    });
};

Plugin.prototype.onRender = function(div) {
    log('Dev info plugin render', div);
    this.div = div.find('.plugin-content');
    var li = $(document.createElement('li')).appendTo($(div).find('.plugin-nav'))
    var a = $(document.createElement('a')).attr('href', '#').appendTo(li);
    a.text('Refresh');
    a.on('click', function(evt) {
        this.startRefresh();
        return false;
    }.bind(this));
    $(html).appendTo(this.div);
    this.progress = this.div.find('.b_progress');
    this.progressBar = this.div.find('.b_bar');
    this.dl = this.div.find('.d_info');
    this.startRefresh();
};

Plugin.prototype.refresh = function(data) {
    var old = this.data;
    if (data) {
        this.data = data;
    }
    var type = 'normal';
    var progressClass = 'b_progress progress';
    if (data.battery_status == 'charging') {
        type = 'warning';
        progressClass += ' progress-warning';
    }
    if (data.battery_status == 'full') {
        type = 'no';
        progressClass += ' progress-success';
    }
    if (!old || old.battery_status != data.battery_status) {
        var message = 'Discharging: '+data.battery_level+'%';
        if (data.battery_status == 'full') {
            message = 'Fully charged';
        }
        if (data.battery_status == 'charging') {
            message = 'Charging: '+data.battery_level+'%';
        }
        this.app.showBallon(this, message);
    }
    this.app.setProgress(this, type, data.battery_level);
    if (!this.div) {
        return false;
    }
    this.progress.attr('class', progressClass);
    this.progressBar.css({
        width: ''+data.battery_level+'%'
    });
    this.dl.empty();
    var addRow = function(title, value) {
        var dt = $(document.createElement('dt')).text(title);
        var dd = $(document.createElement('dd')).text(value);
        this.dl.append(dt);
        this.dl.append(dd);
    }.bind(this);
    addRow('Level:', ''+data.battery_level+'%');
    addRow('Temperature:', ''+(data.battery_temp/10)+'C');
    addRow('Updated:', ''+(new Date().format('H:MM:ss')));
    return true;
}

Plugin.prototype.onShow = function() {
    log('Dev info plugin show');
};
Plugin.prototype.onHide = function() {
    log('Dev info plugin hide');
};
Plugin.prototype.onClose = function() {
    log('Dev info plugin closed');
    this.div = null;
};

Plugin.prototype.onMessage = function(message, ctx) {
    log('Dev info onMessage', message, ctx);
    if (ctx.from == 'devinfo') {
        if (message.type == 'status') {
            log('Refreshing status:', message);
            this.refresh(message);
        }
    }
};

Plugin.pluginName = 'devinfo';
Plugin.pluginDescription = 'Device Info';

registerPlugin(Plugin);
}).call();