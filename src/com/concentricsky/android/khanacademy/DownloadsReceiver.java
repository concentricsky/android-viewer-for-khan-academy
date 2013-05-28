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

import static com.concentricsky.android.khanacademy.Constants.ACTION_UPDATE_DOWNLOAD_STATUS;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_ID;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.concentricsky.android.khanacademy.app.ManageDownloadsActivity;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.util.Log;

public class DownloadsReceiver extends BroadcastReceiver {
	
	public static final String LOG_TAG = DownloadsReceiver.class.getSimpleName();
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
			//just open the manage activity
			intent.setClass(context, ManageDownloadsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			context.startActivity(intent);
		}
		
		else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
			Bundle extras = intent.getExtras();
			
			// This is the only extra provided.
			long id = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID);
			Log.d(LOG_TAG, "Download Complete: " + id);
			
			Intent service = new Intent(context, KADataService.class);
			service.setAction(ACTION_UPDATE_DOWNLOAD_STATUS);
			service.putExtra(EXTRA_ID, id);
			context.startService(service);
		}
	}
}
