package org.kvj.foxtrot7.dispatcher.controller;

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

import org.json.JSONException;
import org.json.JSONObject;
import org.kvj.bravo7.ipc.RemoteServicesCollector;
import org.kvj.bravo7.log.Logger;
import org.kvj.foxtrot7.F7Constants;
import org.kvj.foxtrot7.aidl.F7MessageContext;
import org.kvj.foxtrot7.aidl.F7MessagePlugin;
import org.kvj.foxtrot7.aidl.F7MessageProvider;
import org.kvj.foxtrot7.aidl.PJSONObject;
import org.kvj.foxtrot7.dispatcher.F7App;

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

public class F7Controller {

    Logger logger = Logger.forInstance(this);

    public static final String f7UUID = "EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DC";
    public static final String F7_V2_UUID = "EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DE";

	private static final int AUTOCLOSE_MSEC = 1000 * 60 * 5;
	BluetoothAdapter adapter = null;

    private ServerThread twoWayServer = null;
    private ServerThread oneWayServer = null;
	private RemoteServicesCollector<F7MessagePlugin> plugins = null;
	private DBProvider db = null;
	private final Map<String, BluetoothConnection> connections = new HashMap<>();
	Timer autoCloseTimer = new Timer("AutoClose");

	class BluetoothConnection {

        BluetoothConnection(ServerType serverType) {
            this.serverType = serverType;
        }

        ServerType serverType;

        class CloseTask extends TimerTask {

			@Override
			public void run() {
				logger.d("About to auto close:", device);
				synchronized (connections) {
					if (System.currentTimeMillis() >= cancelTime) {
						connections.remove(device);
                        logger.d("Closing:", device);
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

        public synchronized void close() {
            try {
                if (socket.isConnected()) {
                    OutputStream out = socket.getOutputStream();
                    out.write(0);
                    out.flush();
                }
            } catch (Exception e) {
            }
            try {
                socket.close();
            } catch (Exception e) {
            }
            if (null != task) {
                task.cancel();
            }
        }
	}

	private class BTStateListener extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            logger.i("BT state changed:", state);
            if (null != oneWayServer) {
                oneWayServer.close();
                oneWayServer = null;
            }
            if (null != twoWayServer) {
                twoWayServer.close();
                twoWayServer = null;
            }
			if (state == BluetoothAdapter.STATE_ON) {
				boolean result = startBluetoothListener();
				logger.i("Starting BT:", result);
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
			logger.e("Error opening DB");
			db = null;
		}
	}

	private BluetoothConnection getConnection(String device) {
		synchronized (connections) {
			BluetoothConnection conn = connections.get(device);
			if (null == conn) {
				UUID uuid = UUID.fromString(F7_V2_UUID);
				try {
					logger.d("Creating new connection: " + device);
					BluetoothDevice bdevice = null;
                    for (BluetoothDevice bluetoothDevice : adapter.getBondedDevices()) {
                        logger.d("We know device:", bluetoothDevice.getName(), bluetoothDevice.getAddress(), bluetoothDevice.getBondState());
                        if (bluetoothDevice.getAddress().equalsIgnoreCase(device)) {
                            bdevice = bluetoothDevice;
                            break;
                        }
                    }
                    if (null == bdevice) {
                        logger.e("Device not found:", device);
                        return null;
                    }
                    BluetoothSocket socket = bdevice.createInsecureRfcommSocketToServiceRecord(uuid);
					socket.connect();
					conn = new BluetoothConnection(ServerType.OneWay);
					conn.device = device;
					conn.socket = socket;
				} catch (Exception e) {
                    logger.e(e, "Connection error");
					return null;
				}
				connections.put(device, conn);
			} else {
				logger.d("Reusing connection: " + device);
				return conn;
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
	
	class ClientThread extends Thread {
		
		private static final String LOCK_NAME = "F7";
		BluetoothConnection connection;
		private WakeLock lock;
		
		public ClientThread(BluetoothSocket s, ServerType type) {
			connection = new BluetoothConnection(type);
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
			logger.d("Read byte "+result);
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
            logger.d("readIntoFile: cache:", cache.getAbsolutePath(), tempFile.getAbsolutePath());
			FileOutputStream outStream = new FileOutputStream(tempFile);
			tempFile.deleteOnExit();
			byte[] buffer = new byte[1024];
            logger.d("readIntoFile: read start", size);
			while (readTotal < size) {
				int read = in.read(buffer);
				if (-1 == read) {
					outStream.close();
					logger.w("readIntoFile: no data");
					return null;
				}
				readTotal += read;
				// Log.d(TAG, "readIntoFile: " + size+", "+readTotal);
				outStream.write(buffer, 0, read);
			}
			outStream.close();
			logger.d("readIntoFile done:", size, readTotal, tempFile.getAbsolutePath());
			return tempFile.getAbsolutePath();
		}
		
		@Override
		public void run() {
			logger.i("Incoming bt connection:", connection.socket.getRemoteDevice().getName());
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
						logger.w("Reached end:", read);
						break;
					}
					String data = new String(chs, "utf-8");
                    logger.d("Incoming data[" + size + "] - ", data);
					int result = 0;
					lock.acquire();
					try {
						JSONObject json = new JSONObject(data);
						int dataSize = json.optInt("binary", 0);
						String binaryFile = null;
						if (dataSize>0) {
                            logger.d("Also have binary:", dataSize);
							String tempFile = readIntoFile(in, dataSize);
							// Have binary data right after
                            logger.d("Saved:", binaryFile);
							if (null != tempFile) {
								binaryFile  = tempFile;
							}
						}
						result = processIncomingJSON(address, json, binaryFile);
					} catch (Exception e) {
                        logger.e(e, "Failed to read data:");
						result = F7Constants.F7_ERR_DATA;
					} finally {
						lock.release();
					}
                    if (connection.serverType == ServerType.OneWay) {
                        // Write result
                        out.write(result);
                        out.flush();
                    }
				}
				// Log.i(TAG, "Incoming bt connection done");
			} catch (Exception e) {
                logger.e(e, "Error reading BT:");
			} finally {
                connection.close();
            }
            synchronized (connections) {
				connections.remove(address);
			}
		}
	}

    enum ServerType {OneWay, TwoWay};

	class ServerThread extends Thread {

        private final ServerType type;
        private final UUID uuid;
        BluetoothServerSocket socket = null;
        boolean active = false;

		public ServerThread(String uuid, ServerType type) {
            this.type = type;
            this.uuid = UUID.fromString(uuid);
		}

        public void open() {
            try {
                logger.d("Opening:", type);
                socket = adapter.listenUsingRfcommWithServiceRecord("Foxtrot7_"+type, uuid);
                active = true;
                start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

		@Override
		public void run() {
			try { // Accept errors
				BluetoothSocket s = null;
                logger.d("About to begin listen: ", type);
				while ((s = socket.accept()) != null) {
                    logger.d("New connection accepted");
					synchronized (connections) {
						// Create new connection
						ClientThread thread = new ClientThread(s, type);
                        if (type == ServerType.TwoWay) {
                            connections.put(s.getRemoteDevice().getAddress(), thread.connection);
                        }
						thread.start();
					}
				}
			} catch (Exception e) {
                active = false;
                logger.w("BT Accept error, possibly BT adapter stopped");
			}
		}

        public void close() {
            if (null != socket) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
                socket = null;
                active = false;
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
            logger.d("Sending via BT:", json);
			int result = send(json.toString(), ctx, true);
			return result;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return F7Constants.F7_ERR_DATA;
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
                logger.d("Sending: ", bytes.length, data.length());
				try {
					intToByteArray(bytes.length, output);
                    output.write(bytes);
                    output.flush();
				} catch (Exception e) {
					logger.w("Send failed, connection is broken?");
					if (retry) {
						// Close connection, create another
						synchronized (connections) {
							conn.task.cancel();
							connections.remove(ctx.device);
                            logger.w("Retrying connection");
						}
						return send(data, ctx, false);
					} else {
						// Send failed
                        logger.w("Send failed");
						return F7Constants.F7_ERR_NETWORK;
					}
				}
				if (!TextUtils.isEmpty(ctx.binaryFile)) {
					FileInputStream binaryStream = new FileInputStream(ctx.binaryFile);
					byte[] buffer = new byte[1024];
					int read = 0;
					while ((read = binaryStream.read(buffer))>0) {
						output.write(buffer, 0, read);
					}
					binaryStream.close();
				}
                int result = F7Constants.F7_SUCCESS;
                if (conn.serverType == ServerType.OneWay) {
                    // Check receive result
                    result = in.read();
                    logger.d("Receive result:", result);
                }
				return result;
			}
		} catch (Exception e) {
            logger.e(e, "Error sending");
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
            logger.d("Have plugins:", plugins.size());
			for (F7MessagePlugin pl : plugins) {
				if (pl.onMessage(pdata, ctx)) {
					return F7Constants.F7_SUCCESS;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
        return F7Constants.F7_ERR_DATA;
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
            logger.w("Bluetooth not supported");
			return false;
		}
		if (adapter.getState() != BluetoothAdapter.STATE_ON) {
            logger.w("Bluetooth not enabled");
			return false;
		}
		try { // Bluetooth open errors
            logger.i("Open RFCOMM");
            twoWayServer = new ServerThread(f7UUID, ServerType.TwoWay);
            twoWayServer.open();
            oneWayServer = new ServerThread(F7_V2_UUID, ServerType.OneWay);
            oneWayServer.open();
			return true;
		} catch (Exception e) {
            logger.e(e, "Error opening bluetooth");
		}
		return false;
	}

	private long lastID = 0;

	private synchronized long nextID() {
		long next = System.currentTimeMillis();
		if (next <= lastID) {
			next = lastID+1;
		}
		lastID = next;
		return next;
	}

	private int findAndSend(JSONObject data, F7MessageContext ctx) {
		db.getDatabase().beginTransaction();
        List<String> targetDevices = new ArrayList<>();
		try {
            if (ctx.id == 0) {
                ctx.id = nextID();
            }
			String where = "plugin=?";
			String[] whereArgs = { ctx.from };
			if (!TextUtils.isEmpty(ctx.device)) {
				where += " and device=?";
				whereArgs = new String[] { ctx.from, ctx.device.toUpperCase().trim() };
			}
            logger.d("Selecting:", where, whereArgs.length);
			Cursor c = db.getDatabase().query("pairs", new String[] { "active", "device", "id" }, where, whereArgs,
					null, null, "active desc");
			while (c.moveToNext()) {
				String to = c.getString(1);
				int active = c.getInt(0);
				String id = c.getString(2);
                logger.d("Sending:", id, to, ctx.device);
                targetDevices.add(to);
			}
			c.close();
		} catch (Exception e) {
			e.printStackTrace();
            return F7Constants.F7_ERR_DATA; // No devices
		} finally {
			db.getDatabase().endTransaction();
		}
        if (targetDevices.isEmpty()) {
            logger.w("No devices:", ctx.device, ctx.from);
            return F7Constants.F7_ERR_DATA;
        }
        // Send to all target devices
        int worstResult = F7Constants.F7_SUCCESS;
        for (String device : targetDevices) {
            ctx.device = device;
            int result = send(data, ctx);
            if (result != F7Constants.F7_SUCCESS) {
                worstResult = result;
            }
        }
        return worstResult;
	}

	private F7MessageProvider.Stub provider = new F7MessageProvider.Stub() {

		@Override
		public String getDeviceName(String address) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int send(PJSONObject data, F7MessageContext ctx) throws RemoteException {
            logger.d("send from remote");
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
            logger.i("getPluginDevices:", plugin, result);
		} catch (Exception e) {
            logger.e(e, "Error getting devices:");
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
			values.put("device", device.toUpperCase());
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
            logger.e(e, "Error while adding device:");
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
            logger.e(e, "Error while removing device:");
		} finally {
			db.getDatabase().endTransaction();
		}
		return false;
	}
}
