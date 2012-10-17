package org.kvj.foxtrot7.dispatcher.controller;

import org.kvj.bravo7.SuperService;
import org.kvj.foxtrot7.dispatcher.F7App;

public class F7Service extends SuperService<F7Controller, F7App> {

	public F7Service() {
		super(F7Controller.class, "Foxtrot7");
	}
}
