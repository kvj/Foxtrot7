package org.kvj.foxtrot7.dispatcher.ui;

import org.kvj.bravo7.ControllerConnector;
import org.kvj.bravo7.ControllerConnector.ControllerReceiver;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.R;
import org.kvj.foxtrot7.dispatcher.R.id;
import org.kvj.foxtrot7.dispatcher.R.layout;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.controller.F7Service;

import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public class MainConfiguration extends SherlockFragmentActivity implements
		ControllerReceiver<F7Controller> {

	private static final String TAG = "Main";
	ControllerConnector<F7App, F7Controller, F7Service> conn = null;
	PluginList pluginList = null;
	private F7Controller controller = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_configuration);
		pluginList = (PluginList) getSupportFragmentManager().findFragmentById(
				R.id.plugin_list);
		startService(new Intent(this, F7Service.class));
	}

	@Override
	protected void onStart() {
		conn = new ControllerConnector<F7App, F7Controller, F7Service>(this,
				this);
		conn.connectController(F7Service.class);
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		conn.disconnectController();
	}

	@Override
	public void onController(F7Controller controller) {
		if (null == this.controller) {
			this.controller = controller;
			pluginList.setController(controller);
		}
	}

}
