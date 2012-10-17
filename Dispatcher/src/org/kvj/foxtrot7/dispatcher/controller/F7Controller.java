package org.kvj.foxtrot7.dispatcher.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class F7Controller {

	public static final int F7_ERR_HARDWARE = 1;
	public static final int F7_ERR_NETWORK = 2;
	public static final int F7_ERR_DATA = 3;

	public static final String f7UUID = "EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DC";

	private static final String TAG = "F7Controller";
	BluetoothAdapter adapter = null;

	private ServerThread serverThread = null;

	public F7Controller() {
		Log.i(TAG, "Started");
		startBluetoothListener();
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
					Log.i(TAG, "Incoming bt connection: "
							+ s.getRemoteDevice().getName());
					try { //
						BufferedReader br = new BufferedReader(
								new InputStreamReader(s.getInputStream(),
										"utf-8"));
						OutputStream out = s.getOutputStream();
						int size = 0;
						while ((size = br.read()) > 0) {
							if (0 == size) {
								// No more data
								break;
							}
							char[] chs = new char[size];
							br.read(chs);
							String data = new String(chs);
							Log.i(TAG, "Incoming: " + data);
							int result = 0;
							try {
								JSONObject json = new JSONObject(data);
								result = processIncomingJSON(s
										.getRemoteDevice().getAddress(), json);
							} catch (Exception e) {
								result = F7_ERR_DATA;
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

	private int send(String to, String from, Integer inResponse, JSONObject data) {
		JSONObject json = new JSONObject();
		try {
			json.put("from", from);
			json.put("data", data);
			if (null != inResponse) { // Have in response to
				json.put("response", inResponse.intValue());
			}
			return send(to, json.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return F7_ERR_DATA;
	}

	private int send(String to, String data) {
		if (null == adapter) { // No BT
			return F7_ERR_HARDWARE;
		}
		Log.i(TAG, "About to send data " + data + " to " + to);
		try { // Bluetooth errors
			UUID uuid = UUID.fromString(f7UUID);
			BluetoothDevice device = adapter.getRemoteDevice(to);
			BluetoothSocket socket = device
					.createRfcommSocketToServiceRecord(uuid);
			socket.connect();
			Log.i(TAG, "Connected to remote bt");
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream(), "utf-8"));
			InputStream in = socket.getInputStream();
			writer.write(data.length());
			writer.write(data.toCharArray());
			writer.flush();
			Log.i(TAG, "Written");
			int result = in.read();
			writer.write(0); // EOS
			socket.close();
			Log.i(TAG, "Written and closed: " + result);
			return result;
		} catch (Exception e) {
			Log.e(TAG, "Error sending", e);
		}
		return F7_ERR_NETWORK;
	}

	private int processIncomingJSON(String from, JSONObject json) {
		try {
			JSONObject data = new JSONObject();
			data.put("test", "Hi Korea!");
			send(from, "plugin1", null, data);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	private boolean startBluetoothListener() {
		adapter = BluetoothAdapter.getDefaultAdapter();
		if (null == adapter) {
			Log.e(TAG, "Bluetooth not supported");
			return false;
		}
		try { // Bluetooth open errors
			Set<BluetoothDevice> devs = adapter.getBondedDevices();
			if (null != devs) { // Debug devices
				for (BluetoothDevice dev : devs) {
					Log.i(TAG,
							"Paired device: " + dev.getAddress() + " "
									+ dev.getName());
				}
			}
			UUID uuid = UUID.fromString(f7UUID);
			Log.i(TAG, "Open RFCOMM: " + uuid.toString());
			BluetoothServerSocket serverSocket = adapter
					.listenUsingRfcommWithServiceRecord("Foxtrot7", uuid);
			serverThread = new ServerThread(serverSocket);
			serverThread.start();
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error opening bluetooth", e);
		}
		return false;
	}
}
