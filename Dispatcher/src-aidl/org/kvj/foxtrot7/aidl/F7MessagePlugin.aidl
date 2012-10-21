package org.kvj.foxtrot7.aidl;

import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.aidl.F7MessageContext;

interface F7MessagePlugin {

	String getName();
	String getCaption();
	
	boolean onMessage(in PJSONObject message, in F7MessageContext context);
	
	void onConfigurationChange(in String[] devices);
}