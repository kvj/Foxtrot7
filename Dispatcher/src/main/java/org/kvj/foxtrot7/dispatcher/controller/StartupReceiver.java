package org.kvj.foxtrot7.dispatcher.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartupReceiver extends BroadcastReceiver {

	private static final String TAG = "Startup";

	@Override
	public void onReceive(Context context, Intent arg1) {
		Intent serviceIntent = new Intent(context, F7Service.class);
		Log.i(TAG, "Starting at Boot");
		context.startService(serviceIntent);
	}

}
