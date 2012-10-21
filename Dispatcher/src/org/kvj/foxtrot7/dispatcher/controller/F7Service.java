package org.kvj.foxtrot7.dispatcher.controller;

import org.kvj.bravo7.SuperService;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.R;
import org.kvj.foxtrot7.dispatcher.ui.MainConfiguration;

import android.app.Service;
import android.content.Intent;

public class F7Service extends SuperService<F7Controller, F7App> {

	public F7Service() {
		super(F7Controller.class, "Foxtrot7");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return Service.START_STICKY;
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
