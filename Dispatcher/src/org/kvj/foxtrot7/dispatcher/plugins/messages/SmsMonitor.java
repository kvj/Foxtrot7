package org.kvj.foxtrot7.dispatcher.plugins.messages;

import org.kvj.foxtrot7.aidl.F7MessageContext;
import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsMonitor extends BroadcastReceiver {

	private static final String TAG = "SmsMonitor";
	private PluginsController controller = null;

	public SmsMonitor(PluginsController controller) {
		super();
		Log.i(TAG, "SMS monitoring started");
		this.controller = controller;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();
		if (bundle != null) {
			/* Get all messages contained in the Intent */
			Object[] pdus = (Object[]) bundle.get("pdus");
			/* Feed the StringBuilder with all Messages found. */
			for (Object pdu : pdus) {
				SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
				Log.i(TAG,
						"SMS: " + sms.getDisplayOriginatingAddress() + ", " + sms.getIndexOnIcc() + ", "
								+ sms.getProtocolIdentifier() + ", " + sms.getTimestampMillis() + ", "
								+ sms.getDisplayMessageBody());
				PJSONObject data = new PJSONObject();
				try {
					data.put("type", "new");
					data.put("from", sms.getDisplayOriginatingAddress());
					data.put("body", sms.getDisplayMessageBody());
					data.put("contact",
							controller.getMessagesPlugin().findContactByPhone(sms.getDisplayOriginatingAddress()));
					data.put("sent", sms.getTimestampMillis());
					F7MessageContext ctx = new F7MessageContext();
					ctx.from = "messages";
					ctx.broadcast = true;
					controller.getProvider().send(data, ctx);
				} catch (Exception e) {
					Log.e(TAG, "Error while sending new Sms", e);
				}
			}
		}
	}
}
