/*
 	Viewer for Khan Academy
    Copyright (C) 2012 Concentric Sky, Inc.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.concentricsky.android.khanacademy;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.app.AboutActivity;
import com.concentricsky.android.khanacademy.app.ManageDownloadsActivity;
import com.concentricsky.android.khanacademy.app.ShowProfileActivity;

public class MainMenuDelegate {
	
	private final Activity activity;
	
	public MainMenuDelegate(Activity activity) {
		this.activity = activity;
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = activity.getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
        case R.id.menu_about:
        	activity.startActivity(new Intent(activity, AboutActivity.class));
        	return true;
        case R.id.menu_downloads:
			activity.startActivity(new Intent(activity, ManageDownloadsActivity.class));
        	return true;
        case R.id.menu_login:
			// show profile
        	activity.startActivity(new Intent(activity, ShowProfileActivity.class));
			return true;
        case R.id.menu_logout:
        	// Let the activity handle this itself; it has access to the api adapter.
		default:
			return false;
	    }
		
	}

}
