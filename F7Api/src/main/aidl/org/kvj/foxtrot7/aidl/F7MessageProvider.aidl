package org.kvj.foxtrot7.aidl;

import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.aidl.F7MessageContext;

interface F7MessageProvider {

	String getDeviceName(String address);
	int send(in PJSONObject data, in F7MessageContext ctx);
}