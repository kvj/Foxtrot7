package org.kvj.foxtrot7.dispatcher.controller;

import org.kvj.bravo7.SuperService;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.MainConfiguration;
import org.kvj.foxtrot7.dispatcher.R;

public class F7Service extends SuperService<F7Controller, F7App> {

	public F7Service() {
		super(F7Controller.class, "Foxtrot7");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		raiseNotification(R.drawable.ic_notification,
				"Foxtrot 7 dispatcher is running", MainConfiguration.class);
	}

	@Override
	public void onDestroy() {
		hideNotification();
		super.onDestroy();
	}
}
