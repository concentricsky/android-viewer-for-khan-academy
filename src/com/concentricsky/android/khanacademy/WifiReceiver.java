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

import static com.concentricsky.android.khanacademy.Constants.ACTION_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.REQUEST_CODE_ONE_TIME_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.UPDATE_DELAY_FROM_NETWORK_CONNECT;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.util.Log;

/**
 * This reciever launches a library update when we gain network connectivity.
 * 
 * Enabled only when we have missed a scheduled content update thanks to being disconnected.
 */
public class WifiReceiver extends BroadcastReceiver {

	public static final String LOG_TAG = WifiReceiver.class.getSimpleName();
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Log.d(LOG_TAG, "Connectivity event received. Firing library update.");
		
		// This was triggered by a connectivity change some time after a lack of connectivity caused
		// us to miss a scheduled update. We launch an update. The update task will enable or disable
		// this receiver as needed.
		
		// TODO : This system fails if we lose connectivity in the middle of an update. The update fails,
		// but the connectivity receiver is not enabled. Need to listen for connectivity lost during updates
		// and enable this receiver if that happens. Meanwhile, this is close enough. If connectivity breaks
		// mid-update, then we try again in another day when the alarm triggers.
		
		boolean connected = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE);
		
		if (connected) {
			
			Intent updateIntent = new Intent(context, KADataService.class);
			updateIntent.setAction(ACTION_LIBRARY_UPDATE);
			
			// Schedule the update a few seconds from now via alarm manager, so that multiple connectivity
			// events in quick succession result in only one update.
			AlarmManager am = (AlarmManager) context.getSystemService(Activity.ALARM_SERVICE);
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, UPDATE_DELAY_FROM_NETWORK_CONNECT);

			PendingIntent pendingIntent = PendingIntent.getService(context,
					REQUEST_CODE_ONE_TIME_LIBRARY_UPDATE, updateIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
		}
		
	}

}
