package org.kvj.foxtrot7.dispatcher;

import org.kvj.bravo7.ControllerConnector;
import org.kvj.bravo7.ControllerConnector.ControllerReceiver;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.controller.F7Service;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;

public class MainConfiguration extends SherlockActivity implements
		ControllerReceiver<F7Controller> {

	ControllerConnector<F7App, F7Controller, F7Service> conn = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_configuration);
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
	}

}
