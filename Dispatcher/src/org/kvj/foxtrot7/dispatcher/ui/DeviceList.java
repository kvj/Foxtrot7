package org.kvj.foxtrot7.dispatcher.ui;

import java.util.ArrayList;
import java.util.List;

import org.kvj.bravo7.SuperActivity;
import org.kvj.foxtrot7.dispatcher.R;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller.DeviceInfo;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class DeviceList extends SherlockListFragment implements OnItemLongClickListener {

	private F7Controller controller = null;
	private List<DeviceInfo> devices = null;
	private String plugin;

	public DeviceList() {
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public void setController(F7Controller controller) {
		getListView().setOnItemLongClickListener(this);
		this.controller = controller;
		refresh();
	}

	private void addDevice() {
		List<DeviceInfo> all = controller.getAllDevices();
		final List<DeviceInfo> items = new ArrayList<F7Controller.DeviceInfo>();
		for (DeviceInfo dev : all) {
			if (!devices.contains(dev)) {
				items.add(dev);
			}
		}
		AlertDialog.Builder dialog = new Builder(getActivity());
		dialog.setTitle("Select device:");
		dialog.setAdapter(new ArrayAdapter<DeviceInfo>(getActivity(), android.R.layout.simple_list_item_1, items),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						DeviceInfo device = items.get(which);
						if (controller.addDevice(device.address, plugin)) {
							// Added - refresh
							refresh();
						} else {
							// Error
							SuperActivity.notifyUser(getActivity(), "Device not added");
						}
					}
				});
		dialog.show();
	}

	private void refresh() {
		plugin = getActivity().getIntent().getStringExtra(
				PluginConfiguration.PLUGIN);
		devices = controller.getPluginDevices(plugin);
		setListAdapter(new ArrayAdapter<DeviceInfo>(getActivity(),
				android.R.layout.simple_list_item_1, devices));
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.device_list_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_device_add:
			addDevice();
			return true;
		case R.id.menu_device_refresh:
			refresh();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int index, long arg3) {
		if (controller.removeDevice(devices.get(index).address, plugin)) {
			refresh();
		} else {
			// Error removing
			SuperActivity.notifyUser(getActivity(), "Device not removed");
		}
		return true;
	}
}
