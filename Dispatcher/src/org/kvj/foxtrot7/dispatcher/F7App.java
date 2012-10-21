package org.kvj.foxtrot7.dispatcher;

import org.kvj.bravo7.ApplicationContext;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

public class F7App extends ApplicationContext {

	@Override
	protected void init() {
		publishBean(new F7Controller());
		publishBean(new PluginsController(this));
	}

}
