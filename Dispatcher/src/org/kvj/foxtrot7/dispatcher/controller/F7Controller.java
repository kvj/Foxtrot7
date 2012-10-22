package org.kvj.foxtrot7.dispatcher.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.kvj.bravo7.ipc.RemoteServicesCollector;
import org.kvj.foxtrot7.F7Constants;
import org.kvj.foxtrot7.aidl.F7MessageContext;
import org.kvj.foxtrot7.aidl.F7MessagePlugin;
import org.kvj.foxtrot7.aidl.F7MessageProvider;
import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.dispatcher.F7App;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

public class F7Controller {

	public static final String f7UUID = "EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DC";

	private static final String TAG = "F7Controller";

	private static final int AUTOCLOSE_MSEC = 1000 * 30;
	BluetoothAdapter adapter = null;

	private ServerThread serverThread = null;
	private RemoteServicesCollector<F7MessagePlugin> plugins = null;
	private DBProvider db = null;
	private final Map<String, BluetoothConnection> connections = new HashMap<String, BluetoothConnection>();
	Timer autoCloseTimer = new Timer("AutoClose");

	class BluetoothConnection {

		class CloseTask extends TimerTask {

			@Override
			public void run() {
				Log.i(TAG, "About to auto close: " + device);
				synchronized (connections) {
					if (System.currentTimeMillis() >= cancelTime) {
						connections.remove(device);
						Log.i(TAG, "Closing: " + device);
						try {
							OutputStream out = socket.getOutputStream();
							out.write(0);
							out.flush();
							socket.close();
						} catch (Exception e) {
						}
					}
				}
			}

		}

		BluetoothSocket socket;
		String device;
		long cancelTime;

		TimerTask task = null;
	}

	public F7Controller() {
		startBluetoothListener();
		plugins = new RemoteServicesCollector<F7MessagePlugin>(F7App.getInstance(), F7Constants.PLUGIN_INTERFACE) {

			@Override
			public F7MessagePlugin castAIDL(IBinder binder) {
				return F7MessagePlugin.Stub.asInterface(binder);
			}

		};
		db = new DBProvider();
		if (!db.open()) {
			Log.e(TAG, "Error opening DB");
			db = null;
		}
	}

	private BluetoothConnection getConnection(String device) {
		synchronized (connections) {
			BluetoothConnection conn = connections.get(device);
			if (null == conn) {
				UUID uuid = UUID.fromString(f7UUID);
				try {
					Log.i(TAG, "Creating new connection: " + device);
					BluetoothDevice bdevice = adapter.getRemoteDevice(device);
					BluetoothSocket socket = bdevice.createRfcommSocketToServiceRecord(uuid);
					socket.connect();
					conn = new BluetoothConnection();
					conn.device = device;
					conn.socket = socket;
				} catch (Exception e) {
					return null;
				}
				connections.put(device, conn);
			} else {
				Log.i(TAG, "Reusing connection: " + device);
			}
			// Restart handler
			if (null != conn.task) {
				conn.task.cancel();
			}
			conn.cancelTime = System.currentTimeMillis() + AUTOCLOSE_MSEC;
			conn.task = conn.new CloseTask();
			autoCloseTimer.schedule(conn.task, AUTOCLOSE_MSEC);
			return conn;
		}
	}

	class ServerThread extends Thread {

		private BluetoothServerSocket socket = null;

		public ServerThread(BluetoothServerSocket serverSocket) {
			this.socket = serverSocket;
		}

		@Override
		public void run() {
			try { // Accept errors
				BluetoothSocket s = null;
				Log.i(TAG, "About to begin listen: ");
				while ((s = socket.accept()) != null) {
					Log.i(TAG, "Incoming bt connection: " + s.getRemoteDevice().getName());
					try { //
							// BufferedReader br = new BufferedReader(
							// new InputStreamReader(s.getInputStream(),
							// "utf-8"));
						InputStream in = s.getInputStream();
						// Reader r = new InputStreamReader(in, "utf-8");
						OutputStream out = s.getOutputStream();
						int size = 0;
						while ((size = byteArrayToInt(in)) > 0) {
							if (0 == size) {
								// No more data
								break;
							}
							byte[] chs = new byte[size];
							in.read(chs);
							String data = new String(chs, "utf-8");
							Log.i(TAG, "Incoming data[" + size + "]: " + data);
							int result = 0;
							try {
								JSONObject json = new JSONObject(data);
								result = processIncomingJSON(s.getRemoteDevice().getAddress(), json);
							} catch (Exception e) {
								result = F7Constants.F7_ERR_DATA;
							}
							out.write(result);
						}
						s.close();
						Log.i(TAG, "Incoming bt connection done");
					} catch (Exception e) {
						Log.e(TAG, "Error reading BT: " + e);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "BT Accept error:", e);
			}
		}
	}

	private int send(JSONObject data, F7MessageContext ctx) {
		JSONObject json = new JSONObject();
		try {
			json.put("id", ctx.id);
			json.put("from", ctx.from);
			json.put("data", data);
			if (ctx.inResponse > 0) { // Have in response to
				json.put("response", ctx.inResponse);
			}
			if (ctx.serie > 0) { // Have serie
				json.put("serie", ctx.serie);
			}
			int result = send(json.toString(), ctx, true);
			return result;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return F7Constants.F7_ERR_DATA;
	}

	private int byteArrayToInt(InputStream input) throws IOException {
		int result = 0;
		int shift = 0;
		do {
			int b = input.read();
			// Log.i(TAG, "batoi:1 " + b);
			result |= (b & 127) << shift;
			shift += 7;
			// Log.i(TAG, "batoi:3 " + result);
			if ((b & 128) == 0) {
				return result;
			}
			// Log.i(TAG, "batoi:4 " + (b & 128));
		} while (true);
	}

	private void intToByteArray(int value, OutputStream output) throws IOException {
		int i = value;
		while (i != 0) {
			int b = i & 127;
			i >>= 7;
			if (i != 0) {
				b |= 128;
			}
			output.write(b);
		}
	}

	private int send(String data, F7MessageContext ctx, boolean retry) {
		if (null == adapter) { // No BT
			return F7Constants.F7_ERR_HARDWARE;
		}
		Log.i(TAG, "About to send data " + data + " to " + ctx.device);
		try { // Bluetooth errors
			BluetoothConnection conn = getConnection(ctx.device);
			if (null == conn) {
				throw new IOException("No connection to " + ctx.device);
			}
			synchronized (connections) {
				OutputStream output = conn.socket.getOutputStream();
				InputStream in = conn.socket.getInputStream();
				byte[] bytes = data.getBytes("utf-8");
				Log.i(TAG, "Sending: " + bytes.length + ", " + data.length());
				try {
					intToByteArray(bytes.length, output);
				} catch (Exception e) {
					Log.w(TAG, "Send failed, connection is broken?");
					if (retry) {
						// Close connection, create another
						synchronized (connections) {
							conn.task.cancel();
							connections.remove(ctx.device);
							Log.i(TAG, "Retrying connection");
							return send(data, ctx, false);
						}
					} else {
						// Send failed
						return F7Constants.F7_ERR_NETWORK;
					}
				}
				output.write(bytes);
				output.flush();
				Log.i(TAG, "Written");
				int result = in.read();
				Log.i(TAG, "Written and closed: " + result);
				return result;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error sending", e);
		}
		return F7Constants.F7_ERR_NETWORK;
	}

	private int processIncomingJSON(String from, JSONObject json) {
		try {
			F7MessageContext ctx = new F7MessageContext();
			ctx.device = from;
			ctx.from = json.getString("from");
			ctx.id = json.getLong("id");
			if (json.has("response")) {
				ctx.inResponse = json.getLong("response");
			}
			if (json.has("serie")) {
				ctx.serie = json.getLong("serie");
			}
			JSONObject data = json.getJSONObject("data");
			PJSONObject pdata = new PJSONObject(data.toString());
			List<F7MessagePlugin> plugins = this.plugins.getPlugins();
			for (F7MessagePlugin pl : plugins) {
				if (pl.onMessage(pdata, ctx)) {
					return 0;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	public List<DeviceInfo> getAllDevices() {
		List<DeviceInfo> result = new ArrayList<F7Controller.DeviceInfo>();
		if (null != adapter) {
			Set<BluetoothDevice> devs = adapter.getBondedDevices();
			if (null != devs) {
				for (BluetoothDevice dev : devs) {
					DeviceInfo info = new DeviceInfo(dev.getAddress());
					info.name = dev.getName();
					result.add(info);
				}
			}
		} else {
			DeviceInfo info = new DeviceInfo("FAKE");
			info.name = "Fake paired device";
			result.add(info);
		}
		return result;
	}

	private boolean startBluetoothListener() {
		adapter = BluetoothAdapter.getDefaultAdapter();
		if (null == adapter) {
			Log.e(TAG, "Bluetooth not supported");
			return false;
		}
		try { // Bluetooth open errors
			UUID uuid = UUID.fromString(f7UUID);
			Log.i(TAG, "Open RFCOMM: " + uuid.toString());
			BluetoothServerSocket serverSocket = adapter.listenUsingRfcommWithServiceRecord("Foxtrot7", uuid);
			serverThread = new ServerThread(serverSocket);
			serverThread.start();
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error opening bluetooth", e);
		}
		return false;
	}

	private int findAndSend(JSONObject data, F7MessageContext ctx) {
		db.getDatabase().beginTransaction();
		try {
			ctx.id = System.currentTimeMillis();
			String where = "plugin=?";
			String[] whereArgs = { ctx.from };
			if (!TextUtils.isEmpty(ctx.device)) {
				where += " and device=?";
				whereArgs = new String[] { ctx.from, ctx.device };
			}
			Cursor c = db.getDatabase().query("pairs", new String[] { "active", "device", "id" }, where, whereArgs,
					null, null, "active desc");
			while (c.moveToNext()) {
				String to = c.getString(1);
				int active = c.getInt(0);
				String id = c.getString(2);
				ctx.device = to;
				int result = send(data, ctx);
				if (result == 0) {
					if (active == 0) {
						// Not active
						ContentValues values = new ContentValues();
						values.put("active", 1);
						db.getDatabase().update("pairs", values, "id=?", new String[] { id });
					}
					db.getDatabase().setTransactionSuccessful();
					c.close();
					return result;
				} else {
					if (active != 0) {
						// Active
						ContentValues values = new ContentValues();
						values.put("active", 0);
						db.getDatabase().update("pairs", values, "id=?", new String[] { id });
					}
				}
			}
			c.close();
			if (ctx.resend) {
				// Need to send
				// TODO: implement saving
			}
			db.getDatabase().setTransactionSuccessful();
			return F7Constants.F7_ERR_NETWORK; // No devices
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.getDatabase().endTransaction();
		}
		return F7Constants.F7_ERR_HARDWARE;
	}

	private F7MessageProvider.Stub provider = new F7MessageProvider.Stub() {

		@Override
		public String getDeviceName(String address) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int send(PJSONObject data, F7MessageContext ctx) throws RemoteException {
			return findAndSend(data, ctx);
		}
	};

	public Binder getRemoteProvider() {
		return provider;
	}

	public class PluginItem {
		public String name;
		public String caption;

		@Override
		public String toString() {
			return caption;
		}
	}

	public List<PluginItem> getPlugins() {
		List<PluginItem> result = new ArrayList<F7Controller.PluginItem>();
		try {
			List<F7MessagePlugin> plugins = this.plugins.getPlugins();
			List<String> added = new ArrayList<String>();
			for (F7MessagePlugin pl : plugins) {
				if (!added.contains(pl.getName())) {
					added.add(pl.getName());
					PluginItem item = new PluginItem();
					item.name = pl.getName();
					item.caption = pl.getCaption();
					result.add(item);
				}
			}
			Cursor c = db.getDatabase().query("pairs", new String[] { "plugin" }, null, null, "plugin", null, "plugin");
			while (c.moveToNext()) {
				String plugin = c.getString(0);
				if (!added.contains(plugin)) {
					added.add(plugin);
					PluginItem item = new PluginItem();
					item.name = plugin;
					item.caption = "! " + plugin;
					result.add(item);
				}
			}
			c.close();
		} catch (Exception e) {
		}
		return result;
	}

	public class DeviceInfo {

		public DeviceInfo(String address) {
			this.address = address;
			this.name = address;
		}

		public String address;
		public String name;

		@Override
		public String toString() {
			return name;
		}

		@Override
		public boolean equals(Object o) {
			return address.equals(((DeviceInfo) o).address);
		}

		@Override
		public int hashCode() {
			return address.hashCode();
		}
	}

	public List<DeviceInfo> getPluginDevices(String plugin) {
		List<DeviceInfo> result = new ArrayList<F7Controller.DeviceInfo>();
		try {
			List<DeviceInfo> all = getAllDevices();
			Cursor c = db.getDatabase().query("pairs", new String[] { "device" }, "plugin=?", new String[] { plugin },
					null, null, null);
			while (c.moveToNext()) {
				String device = c.getString(0);
				DeviceInfo info = new DeviceInfo(device);
				int index = all.indexOf(info);
				if (-1 != index) {
					info.name = all.get(index).name;
				}
				result.add(info);
			}
			c.close();
			Log.i(TAG, "getPluginDevices: " + plugin + ", " + result.size());
		} catch (Exception e) {
			Log.e(TAG, "Error getting devices: ", e);
		}
		return result;
	}

	public boolean addDevice(String device, String plugin) {
		try {
			Cursor c = db.getDatabase().query("pairs", new String[] { "device" }, "plugin=? and device=?",
					new String[] { plugin, device }, null, null, null);
			if (c.moveToNext()) {
				// Already added
				c.close();
				return true;
			}
			c.close();
			db.getDatabase().beginTransaction();
			ContentValues values = new ContentValues();
			values.put("device", device);
			values.put("plugin", plugin);
			long id = db.getDatabase().insert("pairs", null, values);
			boolean result = false;
			if (id != -1) {
				db.getDatabase().setTransactionSuccessful();
				result = true;
			}
			db.getDatabase().endTransaction();
			return result;
		} catch (Exception e) {
			Log.e(TAG, "Error while adding device:", e);
		}
		return false;
	}

	public boolean removeDevice(String device, String plugin) {
		db.getDatabase().beginTransaction();
		try {
			ContentValues values = new ContentValues();
			values.put("device", device);
			values.put("plugin", plugin);
			db.getDatabase().delete("pairs", "plugin=? and device=?", new String[] { plugin, device });
			db.getDatabase().setTransactionSuccessful();
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error while removing device:", e);
		} finally {
			db.getDatabase().endTransaction();
		}
		return false;
	}
}
