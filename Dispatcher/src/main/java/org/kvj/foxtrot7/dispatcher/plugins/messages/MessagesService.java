package org.kvj.foxtrot7.dispatcher.plugins.messages;

import org.kvj.bravo7.ipc.RemotelyBindableService;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.os.Binder;

public class MessagesService extends RemotelyBindableService<PluginsController, F7App> {

	public MessagesService() {
		super(PluginsController.class);
	}

	@Override
	public Binder getStub() {
		return controller.getMessagesPlugin();
	}

}
