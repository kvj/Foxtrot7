package org.kvj.foxtrot7.dispatcher.plugins.devinfo;

import org.json.JSONException;
import org.kvj.foxtrot7.aidl.DefaultF7Plugin;
import org.kvj.foxtrot7.aidl.F7MessageContext;
import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.os.BatteryManager;
import android.os.RemoteException;
import android.util.Log;

public class DevInfoPugin extends DefaultF7Plugin {

	private static final String TAG = "DevInfo";

	private PluginsController controller = null;

	int batteryPercentage = -1;
	int batteryStatus = 0;

	int batteryTemp = 0;

	public DevInfoPugin(PluginsController controller) {
		this.controller = controller;
	}

	@Override
	public String getName() throws RemoteException {
		return "devinfo";
	}

	@Override
	public String getCaption() throws RemoteException {
		return "Device Info";
	}

	private PJSONObject prepareStatusData() throws JSONException {
		PJSONObject data = new PJSONObject();
		data.put("battery_level", batteryPercentage);
		String status = "unknown";
		switch (batteryStatus) {
		case BatteryManager.BATTERY_STATUS_CHARGING:
			status = "charging";
			break;
		case BatteryManager.BATTERY_STATUS_DISCHARGING:
			status = "discharging";
			break;
		case BatteryManager.BATTERY_STATUS_FULL:
			status = "full";
			break;
		case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
			status = "notcharging";
			break;
		}
		data.put("battery_status_id", batteryStatus);
		data.put("battery_status", status);
		data.put("battery_temp", batteryTemp);
		return data;
	}

	@Override
	public boolean onMessage(PJSONObject message, F7MessageContext context) throws RemoteException {
		if ("devinfo".equals(context.from)) {
			if ("get".equals(message.opt("type"))) {
				F7MessageContext ctx = context.inResponse();
				try {
					PJSONObject data = prepareStatusData();
					send(controller.getProvider(), data, ctx);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	public void reportBatteryChange() {
		Log.i(TAG, "Reporting status change");
		try {
			PJSONObject data = prepareStatusData();
			data.put("type", "status");
			send(controller.getProvider(), data, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
