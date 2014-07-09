package org.kvj.foxtrot7.dispatcher;

import org.kvj.bravo7.ApplicationContext;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;

import com.pushlink.android.PushLink;

public class F7App extends ApplicationContext {

	@Override
	protected void init() {
		publishBean(new F7Controller());
		publishBean(new PluginsController(this));
        String android_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PushLink.start(this, R.drawable.ic_launcher, "c06qk072ncc5a9e2", android_id);
	}

	public static PowerManager.WakeLock getLock(String name) {
		Context context = getInstance();
		PowerManager mgr = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);

		WakeLock lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
		return lock;
	}
}
