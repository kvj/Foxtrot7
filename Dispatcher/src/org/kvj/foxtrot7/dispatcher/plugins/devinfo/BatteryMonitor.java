package org.kvj.foxtrot7.dispatcher.plugins.devinfo;

import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.util.Log;

public class BatteryMonitor extends BroadcastReceiver {

	private static final String TAG = "Battery";
	private static final int LEVEL_DIFF = 5;
	private PluginsController controller = null;
	private int prevLevel = 0;
	private int prevStatus = -1;

	public BatteryMonitor(PluginsController controller) {
		this.controller = controller;
		Log.i(TAG, "Monitor created");
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		controller.getDevInfoPlugin().batteryPercentage = level;
		int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
		controller.getDevInfoPlugin().batteryStatus = status;
		int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
		controller.getDevInfoPlugin().batteryTemp = temp;
		Log.i(TAG, "Battery status detected: " + level + ", " + status + ", " + temp);
		if (prevStatus != status || Math.abs(level - prevLevel) >= LEVEL_DIFF) {
			prevLevel = level;
			prevStatus = status;
			controller.getDevInfoPlugin().reportBatteryChange();
		}
	}

}
