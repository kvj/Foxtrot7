package org.kvj.foxtrot7.dispatcher.plugins;

import org.kvj.bravo7.ApplicationContext;
import org.kvj.bravo7.ipc.RemoteServiceConnector;
import org.kvj.foxtrot7.F7Constants;
import org.kvj.foxtrot7.aidl.F7MessageProvider;
import org.kvj.foxtrot7.dispatcher.plugins.devinfo.BatteryMonitor;
import org.kvj.foxtrot7.dispatcher.plugins.devinfo.DevInfoPugin;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class PluginsController {

	protected static final String TAG = "PluginsController";
	private ApplicationContext app = null;
	private DevInfoPugin devInfoPugin = new DevInfoPugin(this);
	RemoteServiceConnector<F7MessageProvider> remote = null;
	private boolean initialized = false;

	public PluginsController(ApplicationContext app) {
		this.app = app;
		remote = new RemoteServiceConnector<F7MessageProvider>(app,
				F7Constants.PROVIDER_INTERFACE, null) {

			@Override
			public F7MessageProvider castAIDL(IBinder binder) {
				return F7MessageProvider.Stub.asInterface(binder);
			}

			@Override
			public void onConnect() {
				super.onConnect();
				Log.i(TAG, "Root iface connected");
				if (!initialized) {
					init();
				}
			}

			@Override
			public void onDisconnect() {
				super.onDisconnect();
				Log.i(TAG, "Root iface disconnected");
			}
		};
	}

	private void init() {
		initialized = true;
		app.registerReceiver(new BatteryMonitor(this), new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	public DevInfoPugin getDevInfoPlugin() {
		return devInfoPugin;
	}

	public F7MessageProvider getProvider() {
		return remote.getRemote();
	}

}
