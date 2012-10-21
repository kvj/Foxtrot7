package org.kvj.foxtrot7.dispatcher.controller;

import org.kvj.bravo7.ipc.RemotelyBindableService;
import org.kvj.foxtrot7.dispatcher.F7App;

import android.os.Binder;

public class F7RemoteProvider extends
		RemotelyBindableService<F7Controller, F7App> {

	public F7RemoteProvider() {
		super(F7Controller.class);
	}

	@Override
	public Binder getStub() {
		return controller.getRemoteProvider();
	}

}
