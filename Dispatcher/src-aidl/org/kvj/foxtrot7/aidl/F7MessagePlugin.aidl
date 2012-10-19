package org.kvj.foxtrot7.aidl;

import org.kvj.foxtrot7.aidl.PJSONObject;

interface F7MessagePlugin {

	String getName();
	String getCaption();
	
	boolean onMessage(in PJSONObject message, long id, String fromDevice, String fromPlugin);
	
	void onConfigurationChange(in String[] devices);
}