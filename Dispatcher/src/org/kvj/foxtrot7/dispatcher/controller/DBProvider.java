package org.kvj.foxtrot7.dispatcher.controller;

import org.kvj.bravo7.DBHelper;
import org.kvj.foxtrot7.dispatcher.F7App;

import android.database.sqlite.SQLiteDatabase;

public class DBProvider extends DBHelper {

	public DBProvider() {
		super(F7App.getInstance(), "foxtrot7", 3);
	}

	@Override
	public void migrate(SQLiteDatabase db, int version) {
		switch (version) {
		case 1:
			db.execSQL("create table pairs (id integer primary key autoincrement, plugin text, device text);");
			db.execSQL("create table messages (id integer primary key autoincrement, from_plugin text, device text, response integer, serie integer);");
			break;
		case 2:
			db.execSQL("alter table pairs add active integer default 0;");
			break;
		case 3:
			db.execSQL("alter table messages add data text;");
			break;
		}
	}
}
