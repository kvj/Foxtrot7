package org.kvj.foxtrot7.dispatcher.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
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
				Log.d(TAG, "About to auto close: " + device);
				synchronized (connections) {
					if (System.currentTimeMillis() >= cancelTime) {
						connections.remove(device);
						Log.d(TAG, "Closing: " + device);
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
		Object waitingNow = null;

		TimerTask task = null;
	}

	private class BTStateListener extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
			Log.i(TAG, "BT state changed: " + state);
			if (null != serverThread) {
				try {
					Log.i(TAG, "Stopping BT connections:");
					serverThread.socket.close();
				} catch (Exception e) {
				}
			}
			if (state == BluetoothAdapter.STATE_ON) {
				boolean result = startBluetoothListener();
				Log.i(TAG, "Starting BT: " + result);
			}
		}

	}

	public F7Controller() {
		F7App.getInstance().registerReceiver(new BTStateListener(),
				new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
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
				return null;
				/*
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
				*/
			} else {
				Log.d(TAG, "Reusing connection: " + device);
				return conn;
			}
			// Restart handler
			/*
			if (null != conn.task) {
				conn.task.cancel();
			}
			conn.cancelTime = System.currentTimeMillis() + AUTOCLOSE_MSEC;
			conn.task = conn.new CloseTask();
			autoCloseTimer.schedule(conn.task, AUTOCLOSE_MSEC);
			return conn;
			*/
		}
	}
	
	class ClientThread extends Thread {
		
		private static final String LOCK_NAME = "F7";
		BluetoothConnection connection;
		private WakeLock lock;
		
		public ClientThread(BluetoothSocket s) {
			connection = new BluetoothConnection();
			connection.socket = s;
			lock = F7App.getLock(LOCK_NAME);
		}
		
		private int readUntil(InputStream stream) throws IOException {
			// Log.d(TAG, "Will read four bytes");
			int result = 0;
			for (int i = 0; i < 4; i++) {
				int b = stream.read();
				if (b<0) {
					return -1;
				}
				// Log.d(TAG, "Read byte "+i+" = "+b);
				result = (result << 8) + b;
			}
			Log.d(TAG, "Read byte "+result);
			return result;
		}
		
		private int readInto(InputStream in, byte[] buffer) throws IOException {
			int readTotal = 0;
			while (readTotal < buffer.length) {
				int read = in.read(buffer, readTotal, buffer.length - readTotal);
				if (-1 == read) {
					return -1;
				}
				readTotal += read;
			}
			return readTotal;
		}
		
		private String readIntoFile(InputStream in, int size) throws IOException {
			int readTotal = 0;
			File cache = F7App.getInstance().getExternalCacheDir();
			if (null == cache) {
				cache = F7App.getInstance().getExternalFilesDir(null);
			}
			if (null == cache) {
				cache = Environment.getExternalStorageDirectory();
			}
			File tempFile = new File(cache, "f7b"+System.currentTimeMillis()+".bin");
			Log.d(TAG, "readIntoFile: cache: " + cache.getAbsolutePath()+", "+tempFile.getAbsolutePath());
			FileOutputStream outStream = new FileOutputStream(tempFile);
			tempFile.deleteOnExit();
			byte[] buffer = new byte[1024];
			Log.d(TAG, "readIntoFile: read start " + size);
			while (readTotal < size) {
				int read = in.read(buffer);
				if (-1 == read) {
					outStream.close();
					Log.w(TAG, "readIntoFile: no data");
					return null;
				}
				readTotal += read;
				// Log.d(TAG, "readIntoFile: " + size+", "+readTotal);
				outStream.write(buffer, 0, read);
			}
			outStream.close();
			Log.d(TAG, "readIntoFile done: " + size+", "+readTotal+", "+tempFile.getAbsolutePath());
			return tempFile.getAbsolutePath();
		}
		
		@Override
		public void run() {
			Log.i(TAG, "Incoming bt connection: " + connection.socket.getRemoteDevice().getName());
			String address = connection.socket.getRemoteDevice().getAddress();
			try { //
					// BufferedReader br = new BufferedReader(
					// new InputStreamReader(s.getInputStream(),
					// "utf-8"));
				InputStream in = connection.socket.getInputStream();
				// Reader r = new InputStreamReader(in, "utf-8");
				OutputStream out = connection.socket.getOutputStream();
				
				while (true) {
					int value = readUntil(in);
					if (value <= 0) {
						// Failed or no data
						break;
					}
					int size = value;
					byte[] chs = new byte[size];
					int read = readInto(in, chs);
					if (-1 == read) {
						Log.w(TAG, "Reached end: " + read);
						break;
					}
					String data = new String(chs, "utf-8");
					Log.d(TAG, "Incoming data[" + size + "] - "+ data);
					int result = 0;
					lock.acquire();
					try {
						JSONObject json = new JSONObject(data);
						int dataSize = json.optInt("binary", 0);
						String binaryFile = null;
						if (dataSize>0) {
							Log.d(TAG, "Also have binary: "+dataSize);
							String tempFile = readIntoFile(in, dataSize);
							// Have binary data right after
							Log.d(TAG, "Saved: "+binaryFile);
							if (null != tempFile) {
								binaryFile  = tempFile;
							}
						}
						result = processIncomingJSON(address, json, binaryFile);
					} catch (Exception e) {
						Log.e(TAG, "Failed to read data:", e);
						result = F7Constants.F7_ERR_DATA;
					} finally {
						lock.release();
					}
				}
				connection.socket.close();
				// Log.i(TAG, "Incoming bt connection done");
			} catch (Exception e) {
				Log.e(TAG, "Error reading BT: " + e.getMessage());
				try {
					connection.socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			synchronized (connections) {
				connections.remove(address);
			}
		}
	}
	
	class ServerThread extends Thread {

		BluetoothServerSocket socket = null;

		public ServerThread(BluetoothServerSocket serverSocket) {
			this.socket = serverSocket;
		}

		@Override
		public void run() {
			try { // Accept errors
				BluetoothSocket s = null;
				Log.d(TAG, "About to begin listen: ");
				while ((s = socket.accept()) != null) {
					Log.d(TAG, "New connection accepted");
					synchronized (connections) {
						// Create new connection
						ClientThread thread = new ClientThread(s);
						connections.put(s.getRemoteDevice().getAddress(), thread.connection);
						thread.start();
					}
				}
			} catch (Exception e) {
				Log.w(TAG, "BT Accept error, possibly BT adapter stopped");
			}
			serverThread = null;
			synchronized (connections) {
				connections.clear();
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
			if (!TextUtils.isEmpty(ctx.binaryFile)) {
				File file = new File(ctx.binaryFile);
				json.put("binary", file.length());
			}
			Log.d(TAG, "Sending via BT: "+json);
			int result = send(json.toString(), ctx, true);
			return result;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return F7Constants.F7_ERR_DATA;
	}

	private int byteArrayToInt(InputStream input) throws IOException {
		int result = 0;
		for (int i = 0; i < 4; i++) {
			int b = input.read();
			Log.d(TAG, "Read byte "+i+" = "+b);
			result = (result << 8) + b;
		}
		Log.d(TAG, "Read byte "+result);
		return result;
	}

	private void intToByteArray(int value, OutputStream output) throws IOException {
		// Write
		for (int i = 0; i < 4; i++) {
			output.write(value >> 24);
			value = value << 8;
		}
		output.flush();
	}

	private int send(String data, F7MessageContext ctx, boolean retry) {
		if (null == adapter) { // No BT
			return F7Constants.F7_ERR_HARDWARE;
		}
		// Log.i(TAG, "About to send data " + data + " to " + ctx.device);
		try { // Bluetooth errors
			BluetoothConnection conn = getConnection(ctx.device);
			if (null == conn) {
				throw new IOException("No connection to " + ctx.device);
			}
			synchronized (conn) {
				OutputStream output = conn.socket.getOutputStream();
				InputStream in = conn.socket.getInputStream();
				byte[] bytes = data.getBytes("utf-8");
				Log.d(TAG, "Sending: " + bytes.length + ", " + data.length());
				try {
					intToByteArray(bytes.length, output);
				} catch (Exception e) {
					// Log.w(TAG, "Send failed, connection is broken?");
					if (retry) {
						// Close connection, create another
						synchronized (connections) {
							conn.task.cancel();
							connections.remove(ctx.device);
							Log.w(TAG, "Retrying connection");
						}
						return send(data, ctx, false);
					} else {
						// Send failed
						return F7Constants.F7_ERR_NETWORK;
					}
				}
				output.write(bytes);
				output.flush();
				if (!TextUtils.isEmpty(ctx.binaryFile)) {
					FileInputStream binaryStream = new FileInputStream(ctx.binaryFile);
					byte[] buffer = new byte[1024];
					int read = 0;
					while ((read = binaryStream.read(buffer))>0) {
						output.write(buffer, 0, read);
					}
					binaryStream.close();
				}
				return 0;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error sending", e);
		}
		return F7Constants.F7_ERR_NETWORK;
	}

	private int processIncomingJSON(String from, JSONObject json, String binaryFile) {
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
			if (null != binaryFile) {
				ctx.binaryFile = binaryFile;
			}
			JSONObject data = json.getJSONObject("data");
			PJSONObject pdata = new PJSONObject(data.toString());
			List<F7MessagePlugin> plugins = this.plugins.getPlugins();
			Log.d(TAG, "Have plugins: "+plugins.size());
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
		if (adapter.getState() != BluetoothAdapter.STATE_ON) {
			Log.w(TAG, "Bluetooth not enabled");
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

	private long lastID = 0;

	private long nextID() {
		long next = System.currentTimeMillis();
		while (next <= lastID) {
			next++;
		}
		lastID = next;
		return next;
	}

	private int findAndSend(JSONObject data, F7MessageContext ctx) {
		db.getDatabase().beginTransaction();
		try {
			ctx.id = nextID();
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
					if (!ctx.broadcast) {
						// Not a broadcast - done
						db.getDatabase().setTransactionSuccessful();
						c.close();
						return result;
					}
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