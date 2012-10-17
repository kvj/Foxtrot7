package org.kvj.foxtrot7.dispatcher.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartupReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent arg1) {
		Intent serviceIntent = new Intent(context, F7Service.class);
		context.startService(serviceIntent);
	}

}
