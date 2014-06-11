package org.kvj.foxtrot7.dispatcher.plugins.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kvj.foxtrot7.aidl.DefaultF7Plugin;
import org.kvj.foxtrot7.aidl.F7MessageContext;
import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.dispatcher.F7App;
import org.kvj.foxtrot7.dispatcher.plugins.PluginsController;

import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsManager;
import android.util.Log;

public class MessagesPlugin extends DefaultF7Plugin {

	interface SendResult {
		public void sent(String error);
	}

	class SendContext {
		int parts;
		String error = null;
		int partsReceived = 0;
		SendResult result = null;
	}

	static final String ID = "id";

	public static final String TAG = "Messages";

	static Map<Long, SendContext> pendingSends = new HashMap<Long, SendContext>();

	private PluginsController controller = null;

	public MessagesPlugin(PluginsController controller) {
		this.controller = controller;
	}

	@Override
	public String getName() throws RemoteException {
		return "messages";
	}

	@Override
	public String getCaption() throws RemoteException {
		return "Messages";
	}

	private void sendSms(String to, String body, long id, SendResult result) {
		SmsManager sms = SmsManager.getDefault();
		ArrayList<String> parts = sms.divideMessage(body);
		SendContext ctx = new SendContext();
		ctx.result = result;
		synchronized (pendingSends) {
			pendingSends.put(id, ctx);
		}
		ctx.parts = parts.size();
		Intent sentIntent = new Intent(F7App.getInstance(), SendResultReceiver.class);
		sentIntent.putExtra(ID, id);
		PendingIntent pIntent = PendingIntent.getBroadcast(F7App.getInstance(), (int) id, sentIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		// ArrayList<PendingIntent> intents = new ArrayList<PendingIntent>();
		for (int i = 0; i < parts.size(); i++) {
			sms.sendTextMessage(to, null, parts.get(i), pIntent, null);
		}
		Log.i(TAG, "Message sent: " + parts.size());
	}

	class MessageItem {
		String contact;
		String from;
		long id;
		long sent;
		String body;
		boolean unread;
		int total;
	}

	private static String SMS_ID = "_id";
	private static String SMS_FROM = "address";
	private static String SMS_PERSON = "person";
	private static String SMS_DATE = "date";
	private static String SMS_READ = "read";
	private static String SMS_BODY = "body";

	@Override
	public boolean onMessage(PJSONObject message, final F7MessageContext context) throws RemoteException {
		if ("messages".equals(context.from)) {
			if ("send".equals(message.opt("type"))) {
				// Send this
				sendSms(message.optString("to"), message.optString("body"), context.id, new SendResult() {

					@Override
					public void sent(String error) {
						Log.i(TAG, "Sms message sent: " + error);
						F7MessageContext ctx = context.inResponse();
						PJSONObject response = new PJSONObject();
						try {
							response.put("error", error);
							send(controller.getProvider(), response, ctx);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				return false;
			}
			if ("list".equals(message.opt("type"))) {
				int skip = message.optInt("skip", 0);
				int limit = message.optInt("limit", 10);
				String folder = message.optString("folder", "inbox");
				List<MessageItem> messages = getMessageList(folder, skip, limit);
				F7MessageContext ctx = context.inResponse();
				PJSONObject resp = new PJSONObject();
				try {
					if (null == messages) {
						resp.put("error", "Folder read error");
					} else {
						JSONArray arr = new JSONArray();
						for (MessageItem item : messages) {
							JSONObject json = new JSONObject();
							json.put("from", item.from);
							json.put("contact", item.contact);
							json.put("body", item.body);
							json.put("sent", item.sent);
							json.put("id", item.id);
							json.put("unread", item.unread);
							arr.put(json);
						}
						resp.put("items", arr);
						resp.put("total", messages.size() > 0 ? messages.get(0).total : skip);
					}
					send(controller.getProvider(), resp, ctx);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return super.onMessage(message, context);
	}

	String findContactByPhone(String phone) {
		try {
			Uri personUri = Uri.withAppendedPath(
					ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
			Cursor cur = F7App.getInstance().getContentResolver().query(personUri,
					new String[] { PhoneLookup.DISPLAY_NAME },
					null, null, null);
			if (cur.moveToFirst()) {
				int nameIdx = cur.getColumnIndex(PhoneLookup.DISPLAY_NAME);
				String name = cur.getString(nameIdx);
				// Log.i(TAG, "Phone: " + name + ", " + phone);
				cur.close();
				return name;
			}
			cur.close();
			// Log.i(TAG, "Contact not found: " + phone + ", " + personUri);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private List<MessageItem> getMessageList(String folder, int skip, int limit) {
		List<MessageItem> items = new ArrayList<MessagesPlugin.MessageItem>();
		try {
			Uri uri = Uri.parse("content://sms/" + folder);
			Cursor cursor = F7App
					.getInstance()
					.getContentResolver()
					.query(uri, new String[] { SMS_ID, SMS_BODY, SMS_DATE, SMS_FROM, SMS_PERSON, SMS_READ }, null,
							null, null);
			if (!cursor.moveToFirst()) {
				// Error
				Log.w(TAG, "Empty folder?");
				cursor.close();
				return items;
			}
			int itemsAdded = 0;
			int index = 0;
			do {
				if (index < skip) {
					index++;
				} else {
					itemsAdded++;
					MessageItem item = new MessageItem();
					item.id = cursor.getLong(0);
					item.body = cursor.getString(1);
					item.sent = cursor.getLong(2);
					item.from = cursor.getString(3);
					item.unread = 1 != cursor.getInt(5);
					item.contact = findContactByPhone(item.from);
					items.add(item);
					if (itemsAdded >= limit) {
						break;
					}
				}
			} while (cursor.moveToNext());
			if (items.size() > 0) {
				items.get(0).total = cursor.getCount();
			}
			cursor.close();
		} catch (Exception e) {
			Log.e(TAG, "Error reading:", e);
			return null;
		}
		return items;
	}

}
