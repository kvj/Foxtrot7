package org.kvj.foxtrot7.aidl;

import org.kvj.foxtrot7.F7Constants;

import android.os.RemoteException;

abstract public class DefaultF7Plugin extends F7MessagePlugin.Stub {

	@Override
	public void onConfigurationChange(String[] devices) throws RemoteException {
	}

	@Override
	public boolean onMessage(PJSONObject message, F7MessageContext context) throws RemoteException {
		return false;
	}

	protected int send(F7MessageProvider provider, PJSONObject data, F7MessageContext ctx) throws RemoteException {
		if (null == provider) {
			return F7Constants.F7_ERR_NETWORK;
		}
		if (null == ctx) {
			ctx = new F7MessageContext();
		}
		ctx.from = getName();
		return provider.send(data, ctx);
	}

}
