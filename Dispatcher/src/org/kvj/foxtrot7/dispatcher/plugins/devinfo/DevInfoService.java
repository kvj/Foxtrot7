package org.kvj.foxtrot7.dispatcher.plugins.devinfo;

import org.kvj.bravo7.ipc.RemotelyBindableService;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.os.Binder;

public class DevInfoService extends
		RemotelyBindableService<PluginsController, F7App> {

	public DevInfoService() {
		super(PluginsController.class);
	}

	@Override
	public Binder getStub() {
		return controller.getDevInfoPlugin();
	}

}
