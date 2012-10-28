(function() {
var Plugin = function () {
}

Plugin.prototype = new PluginStub();

Plugin.pluginName = 'messages';
Plugin.pluginDescription = 'Messaging';

Plugin.prototype.onStart = function () {
    this.messages = {};
};

var html = '<ul class="nav nav-tabs"><li class="active"><a href="#" class="a-mtab-inbox mtab-active">Incoming</a></li><li><a href="#" class="mtab-active">Inbox</a></li><li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">Folders<b class="caret"></b></a><ul class="dropdown-menu"><li><a href="#" class="mtab-active">Outbox</a></li><li><a href="#" class="mtab-active">Drafts</a></li><li><a href="#" class="mtab-active">Sent</a></li><li><a href="#" class="mtab-active">Failed</a></li></ul></li></ul><div class="tab-content"><div class="tab-pane active mtab-inbox"></div><div class="tab-pane mtab-list-inbox"></div><div class="tab-pane mtab-list-outbox"></div><div class="tab-pane mtab-list-drafts"></div><div class="tab-pane mtab-list-sent"></div><div class="tab-pane mtab-list-failed"></div></div>'

var messageHtml = '<div class="one-message well well-small"><div class="one-message-title"><div class="one-message-title-text pull-left"></div><a class="close pull-right one-message-close" href="#">&times;</a></div><div class="one-message-body"></div><button class="btn btn-block btn-primary one-message-reply" type="button">Reply</button><div class="one-message-reply-form"><textarea class="one-message-input"/><p class="pull-right"><button class="btn btn-success one-message-send" type="button">Send</button> <button class="btn btn-danger one-message-cancel" type="button">Cancel</button></p><div class="clear"/></div><small class="one-message-footer"></small></div>'

var listHtml = '<div class="messages-list"><h4><span class="list-title"></span> <button class="btn btn-primary list-refresh" type="button" data-loading-text="Loading...">Refresh</button></h4><table class="list-table table table-striped table-bordered table-hover table-condensed"><thead><tr><th class="th-contact">Contact:</th><th class="th-date">Date:</th><th class="th-body">Text:</th></tr></thead><tbody></tbody></table><div class="pagination pagination-centered list-pager"><ul></ul></div></div>'

var dateFormat = 'm/d H:MM';

Plugin.prototype.onMessage = function(message, ctx) {
//    log('onMessage:', ctx.from, message.type);
    if (ctx.from == 'messages') {
        if (message.type == 'new') {
            this.messages[ctx.id] = {
                ctx: ctx,
                data: message,
                status: 'unread'
            };
            this.renderMessage(this.messages[ctx.id]);
            this.app.showBallon(this, 'Message from: '+(message.contact || message.from));
            this.app.raise(this);
            this.app.focus(this);
            this.raiseInbox();
            log('Incoming SMS:', message);
        }
    }
};
Plugin.prototype.onRender = function(div) {
    this.div = div.find('.plugin-content');
    this.div.html(html);
    var tabs = this.div.find('li a.mtab-active');
    var tabContent = this.div.find('.tab-content div');
    var ids = ['messages', 'inbox', 'outbox', 'sent', 'drafts', 'failed'];
    for(var i = 0; i<ids.length; i++) {
        tabs.eq(i).attr('id', 'a'+ids[i]+this.id).attr('href', '#t'+ids[i]+this.id);
        tabContent.eq(i).attr('id', 't'+ids[i]+this.id);    
    }
    tabs.click(function (e) {
        e.preventDefault();
        $(this).tab('show');
    });
    for (var id in this.messages) {
        this.renderMessage(this.messages[id]);
    }
    var renderList = function(id, folder, title) {
        var pageSize = 10;
        var pagesBefore = 5;
        var pagesVisible = 10;
        var showList = function(data) {
            tableBody.empty();
            var showItem = function(item) {
                var tr = $(document.createElement('tr'));
                if (item.unread) {
                    tr.addClass('unread');
                }
                $(document.createElement('td')).addClass('td-contact').text(item.contact || item.from).appendTo(tr);
                var dt = new Date(item.sent);
                $(document.createElement('td')).addClass('td-date').text(dt.format(dateFormat)).appendTo(tr);
                $(document.createElement('td')).addClass('td-body').text(item.body || '').appendTo(tr);
                tableBody.append(tr);
                tr.children().bind('click', function (e) {
                    this.raiseInbox();
                    if (!this.messages[item.id]) {
                        var m = {
                            ctx: {id: item.id},
                            data: item,
                            status: '['+folder+']'
                        };
                        this.messages[item.id] = m
                        this.renderMessage(m);
                    };
                    e.preventDefault();
                    return false;
                }.bind(this));
            }.bind(this);
            for(var i = 0; i<data.length; i++) {
                showItem(data[i]);
            }
        }.bind(this);
        var showPager = function(page, total) {
            var pagesTotal = Math.ceil(total/pageSize);
            pager.empty();
            var start = Math.max(page-pagesBefore, 0);
            var end = Math.min(start+pagesVisible, pagesTotal);
            var addClick = function(a, index) {
                a.click(function(e){
                    loadList(index);
                }.bind(this));
            };
            if (start>0) {
                var li = $(document.createElement('li')).appendTo(pager);
                var a = $(document.createElement('a')).appendTo(li).attr('href', '#').text('«');
                addClick(a, 0);
            }
            for (var i = start; i<end; i++) {
                var li = $(document.createElement('li')).appendTo(pager);
                var a = $(document.createElement('a')).appendTo(li).attr('href', '#').text(i+1);
                if (i == page) {
                    li.addClass('active');
                } else {
                    addClick(a, i);
                }
            };
            if (end<pagesTotal) {
                var li = $(document.createElement('li')).appendTo(pager);
                var a = $(document.createElement('a')).appendTo(li).attr('href', '#').text('»');
                addClick(a, pagesTotal-1);
            }
        }.bind(this);
        var loadList = function(page) {
            refresh.button('loading');
            this.send({type: 'list', folder: folder, skip: page*pageSize}, {
                onResponse: function(err, data, ctx) {
                    refresh.button('reset');
                    if (err) {
                        return this.showError(err);
                    }
                    if (data.error) {
                        return this.showError(data.error);
                    }
                    showList(data.items, page);
                    if (data.items.length == 0) {
                        this.showAlert('List is empty');
                    }
                    showPager(page, data.total);
                    return null;
                }.bind(this)
            });            
        }.bind(this);
        var root = div.find('.'+id);
        root.html(listHtml);
        var refresh = root.find('.list-refresh');
        var pager = root.find('.list-pager ul');
        var tableBody = root.find('.list-table tbody');
        root.find('.list-title').text(title);
        refresh.click(function () {
            loadList(0);
        }.bind(this));
    }.bind(this);
    renderList('mtab-list-inbox', 'inbox', 'Inbox');
    renderList('mtab-list-outbox', 'outbox', 'Outbox');
    renderList('mtab-list-sent', 'sent', 'Sent');
    renderList('mtab-list-drafts', 'draft', 'Drafts');
    renderList('mtab-list-failed', 'failed', 'Failed');
};

Plugin.prototype.raiseInbox = function() {
    if (!this.div) {
        return;
    }
    this.div.find('.a-mtab-inbox').tab('show');
}

Plugin.prototype.renderMessage = function(message) {
    if (!this.div) {
        return;
    }
    var refreshStatus = function () {
        var dt = new Date(message.data.sent);
        div.find('.one-message-footer').text(dt.format(dateFormat)+' '+message.status);
        if (message.status == 'unread') {
            div.addClass('one-message-unread');
        } else {
            div.removeClass('one-message-unread');
        }
    }.bind(this);
    var div = $(messageHtml);
    this.div.find('.mtab-inbox').prepend(div);
    div.find('.one-message-title-text').text(message.data.contact || message.data.from);
    div.find('.one-message-close').click(function(evt) {
        div.remove();
        delete this.messages[message.ctx.id];
    }.bind(this));
    div.find('.one-message-body').text(message.data.body);
    refreshStatus();
    var input = div.find('.one-message-input');
    var send = div.find('.one-message-send');
    var form = div.find('.one-message-reply-form');
    var reply = div.find('.one-message-reply');
    reply.click(function(evt) {
        form.addClass('one-message-reply-form-visible');
        input.focus();
    }.bind(this));
    input.bind('input', function (evt) {
        var text = input.val();
        send.attr('disabled', text? false: true);
    }.bind(this));
    input.trigger('input');
    div.find('.one-message-cancel').click(function(evt) {
        form.removeClass('one-message-reply-form-visible');
    }.bind(this));
    send.click(function(evt) {
        var sendFinished = function(success, status) {
            reply.attr('disabled', false);
            if (!success) {
                form.addClass('one-message-reply-form-visible');
            } else {
                input.val('');
            }
            if (status) {
                message.status = status;
                refreshStatus();
            }
        }.bind(this);
        var body = input.val();
        form.removeClass('one-message-reply-form-visible');
        reply.attr('disabled', true);
        message.status = 'sending...';
        refreshStatus();
        var msg = {
            type: 'send',
            to: message.data.from,
            body: body
        };
        this.send(msg, {
            onResponse: function(err, data, ctx) {
                log('Sms sent:', data);
                if (err) {
                    sendFinished(false, 'not sent');
                    return this.app.showError(err);
                }
                if (data.error) {
                    sendFinished(false, 'error');
                    return this.app.showError(data.error);
                }
                sendFinished(true, 'replied');
                this.showAlert('Message sent', {severity: 'success'});
                return null;
            }.bind(this)
        });
        
    }.bind(this));
    div.click(function() {
        if (message.status == 'unread') {
            message.status = 'read';
            refreshStatus();
        }
    }.bind(this));
};

Plugin.prototype.onClose = function() {
    this.div = null;
};

registerPlugin(Plugin);
}).call();

