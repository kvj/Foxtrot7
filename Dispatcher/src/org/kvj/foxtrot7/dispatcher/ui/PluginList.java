package org.kvj.foxtrot7.dispatcher.ui;

import java.util.List;

import org.kvj.foxtrot7.dispatcher.R;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller;
import org.kvj.foxtrot7.dispatcher.controller.F7Controller.PluginItem;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class PluginList extends SherlockListFragment {

	private static final String TAG = "PluginList";
	private F7Controller controller = null;
	List<F7Controller.PluginItem> plugins = null;

	public PluginList() {
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	private void refresh() {
		new AsyncTask<Void, Void, List<F7Controller.PluginItem>>() {

			@Override
			protected List<F7Controller.PluginItem> doInBackground(Void... params) {
				return plugins = controller.getPlugins();
			}

			@Override
			protected void onPostExecute(List<F7Controller.PluginItem> result) {
				plugins = result;
				setListAdapter(new ArrayAdapter<PluginItem>(getActivity(),
						android.R.layout.simple_list_item_1, plugins));
			};
		}.execute();
	}

	public void setController(F7Controller controller) {
		this.controller = controller;
		refresh();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		String plugin = plugins.get(position).name;
		Intent intent = new Intent(getActivity(), PluginConfiguration.class);
		intent.putExtra(PluginConfiguration.PLUGIN, plugin);
		startActivity(intent);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.plugin_list_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_plugin_list_refresh:
			refresh();
			return true;
		}
		return false;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}
}
