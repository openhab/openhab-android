package org.openhab.habdroid.ui;

import org.openhab.habdroid.R;
import org.openhab.habdroid.R.layout;
import org.openhab.habdroid.R.menu;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class OpenHABWriteTagActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwritetag);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

}
