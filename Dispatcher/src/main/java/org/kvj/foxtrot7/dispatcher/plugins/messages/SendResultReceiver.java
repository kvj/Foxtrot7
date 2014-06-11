package org.kvj.foxtrot7.dispatcher.plugins.messages;

import org.kvj.foxtrot7.dispatcher.plugins.messages.MessagesPlugin.SendContext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

public class SendResultReceiver extends BroadcastReceiver {

	private static final String TAG = "SendResultReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		synchronized (MessagesPlugin.pendingSends) {
			long id = intent.getLongExtra(MessagesPlugin.ID, 0);
			SendContext ctx = MessagesPlugin.pendingSends.get(id);
			if (null == ctx) {
				Log.w(TAG, "Send context not found");
				return;
			}
			ctx.partsReceived++;
			// TODO: check send result
			switch (getResultCode()) {
			case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
				ctx.error = "General failure";
				break;
			case SmsManager.RESULT_ERROR_NO_SERVICE:
				ctx.error = "No service";
				break;
			case SmsManager.RESULT_ERROR_NULL_PDU:
				ctx.error = "PDU is null";
				break;
			case SmsManager.RESULT_ERROR_RADIO_OFF:
				ctx.error = "No carrier";
				break;
			}
			// Log.i(TAG, "Send result: " + getResultCode());
			if (ctx.partsReceived >= ctx.parts) {
				// Send done
				MessagesPlugin.pendingSends.remove(id);
				ctx.result.sent(ctx.error);
			}
		}
	}

}
