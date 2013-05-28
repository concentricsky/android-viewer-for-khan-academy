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
package com.concentricsky.android.khanacademy.app;

import static com.concentricsky.android.khanacademy.Constants.ACTION_BADGE_EARNED;
import static com.concentricsky.android.khanacademy.Constants.ACTION_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.ACTION_TOAST;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_BADGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_MESSAGE;
import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.REQUEST_CODE_RECURRING_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.TAG_LIST_FRAGMENT;
import static com.concentricsky.android.khanacademy.Constants.UPDATE_DELAY_FROM_FIRST_RUN;

import java.sql.SQLException;
import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.MainMenuDelegate;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;

public class HomeActivity extends KADataServiceProviderActivityBase
		implements TopicListFragment.Callbacks {
	
	public static final String LOG_TAG = HomeActivity.class.getSimpleName();
	
	private MainMenuDelegate mainMenuDelegate;
	private Menu mainMenu;
	private Topic topic;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, final Intent intent) {
			if (ACTION_LIBRARY_UPDATE.equals(intent.getAction()) && topic != null) {
				Log.d(LOG_TAG, "library update broadcast received");
				setListForTopic(topic, TopicListFragment.class, true);
			} else if (ACTION_BADGE_EARNED.equals(intent.getAction())) {
				requestDataService(new ObjectCallback<KADataService>() {
					@Override
					public void call(KADataService dataService) {
						Badge badge = (Badge) intent.getSerializableExtra(EXTRA_BADGE);
						dataService.getAPIAdapter().toastBadge(badge);
					}
				});
			} else if (ACTION_TOAST.equals(intent.getAction())) {
				Toast.makeText(HomeActivity.this, intent.getStringExtra(EXTRA_MESSAGE), Toast.LENGTH_SHORT).show();
			}
		}
		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.setDefaultLevel(Log.SUPPRESS);
		
		// If there is no intent scheduled, set a repeating alarm for library updates.
		setupRepeatingLibraryUpdateAlarm();
		
		setContentView(R.layout.activity_home);
		
		// According to http://stackoverflow.com/a/2791816/931277, setting dither="true" on the shape tag in xml doesn't work.
		// However, using the debugger on the Nexus 7 (4.2), when it's set in xml it is in fact set on this Drawable, so this
		// *should* be unnecessary. It won't hurt, though.
		ImageView splash = (ImageView) findViewById(R.id.activity_home_imageview);
		Drawable background = splash.getBackground();
		if (background instanceof GradientDrawable) {
			((GradientDrawable) background).setDither(true);
		}
	}
	
	private void setupRepeatingLibraryUpdateAlarm() {
		Log.d(LOG_TAG, "setupRepeatingLibraryUpdateAlarm");
		AlarmManager am = (AlarmManager) getSystemService(Activity.ALARM_SERVICE);

		Intent intent = new Intent(getApplicationContext(), KADataService.class);
		intent.setAction(ACTION_LIBRARY_UPDATE);
		PendingIntent existing = PendingIntent.getService(getApplicationContext(),
				REQUEST_CODE_RECURRING_LIBRARY_UPDATE, intent, PendingIntent.FLAG_NO_CREATE);
		boolean alreadyScheduled = existing != null;

		if (!alreadyScheduled) {
			// Initial delay.
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, UPDATE_DELAY_FROM_FIRST_RUN);

			// Schedule the alarm.
			PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(),
					REQUEST_CODE_RECURRING_LIBRARY_UPDATE, intent, PendingIntent.FLAG_CANCEL_CURRENT);
			Log.d(LOG_TAG, "(re)setting alarm");
			am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
		}
	}

	private boolean userHasAcceptedTOS() {
		return getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE)
				.getBoolean(Constants.SETTING_ACKNOWLEDGEMENT, false);
	}
	
	private void showTOSAcknowledgement() {
		
		View contentView = LayoutInflater.from(this).inflate(R.layout.dialog_tos, null, false);
		
		// Use this LinkMovementMethod (as opposed to just the xml setting android:autoLink="web") to allow for named links.
		TextView mustAccept = (TextView) contentView.findViewById(R.id.dialog_tos_must_accept);
		TextView notEndorsed = (TextView) contentView.findViewById(R.id.dialog_tos_not_endorsed);
		mustAccept.setMovementMethod(LinkMovementMethod.getInstance());
		notEndorsed.setMovementMethod(LinkMovementMethod.getInstance());
		
		new AlertDialog.Builder(this)
				.setCancelable(false)
				.setView(contentView)
				.setPositiveButton(getString(R.string.button_i_accept), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Store the fact that the user has accepted.
						getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE)
								.edit()
								.putBoolean(Constants.SETTING_ACKNOWLEDGEMENT, true)
								.apply();
					}
				})
				.setNegativeButton(getString(R.string.button_quit), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				})
				.show();
	}
	
	/*
	 * API TOS
	 * 
	 * We must display prominently:
	 * 
	 * "This product uses the Khan Academy API but is not endorsed or certified by Khan Academy (www.khanacademy.org)."
	 *    and
	 * "All Khan Academy content is available for free at www.khanacademy.org"
	 * 
	 * 
	 * Among the "Trademark and Brand Usage Policy":
	 * As another example, the use by an organization that is incorporating our content in a paid offering is NOT "non-commercial"
	 * 
	 * WE WILL:
	 *  a.	require all users of Your Application to affirmatively agree to be bound by the Khan Academy Terms of Service and the Khan Academy Privacy Policy ;
		b.	notify Khan Academy if any users of Your Application are "repeat infringers" as that term is defined in the Khan Academy Terms of Service ;
		c.	notify Khan Academy if you receive any complaint (including a copyright or other right holder) based on any content that is hosted by Khan Academy;
		d. in connection with your use of the Khan Academy API and the Khan Academy Platform, comply with all applicable local, state, national, and international laws and regulations, including, without limitation, copyright and other laws protecting proprietary rights (including the DMCA) and all applicable export control laws and regulations and country-specific economic sanctions implemented by the United States Office of Foreign Assets Control. For clarity, the foregoing does not limit your representations in Section 4, above;
		e.	provide any information and/or other materials related to Your Applications reasonably requested by Khan Academy from time to time to verify your compliance with these API Terms.
	 * and WILL NOT do a bunch of the usual things.
	 * 
	 * 
	 * 
	 * (non-Javadoc)
	 * @see com.concentricsky.android.khanacademy.app.LifecycleTraceActivity#onStart()
	 */
	
	
	
	
	@Override
	protected void onStart() {
		super.onStart();
		
		if (!userHasAcceptedTOS()) {
			showTOSAcknowledgement();
		}
		
		mainMenuDelegate = new MainMenuDelegate(this);
		
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				topic = dataService.getRootTopic();
				if (topic != null) {
					//  It is important to create the AbstractListFragment programmatically here as opposed to
					// specifying it in the xml layout.  If it is specified in xml, we end up with an
					// error about "Content view not yet created" when clicking a list item after restoring
					// a fragment from the back stack.
					setListForTopic(topic, TopicListFragment.class, true);
				}
			}
		});
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_LIBRARY_UPDATE);
		filter.addAction(ACTION_BADGE_EARNED);
		filter.addAction(ACTION_TOAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
	}
	
	@Override
	protected void onStop() {
		mainMenuDelegate = null;
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onStop();
	}
	
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		Log.d(LOG_TAG, "onCreateOptionsMenu");
		mainMenu = menu;
		// We use a different menu in this activity, so we skip the delegate's onCreateOptionsMenu.
		getMenuInflater().inflate(R.menu.home, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		Log.d(LOG_TAG, "onPrepareOptionsMenu");
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				User user = dataService.getAPIAdapter().getCurrentUser();
				boolean show = user != null;
				mainMenu.findItem(R.id.menu_logout).setEnabled(show).setVisible(show);
			}
		});
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Delegate is still good for this activity, as the home menu contains the same items as the main menu.
		if (mainMenuDelegate.onOptionsItemSelected(item)) {
			return true;
		}
		switch (item.getItemId()) {
	    case R.id.menu_logout:
			requestDataService(new ObjectCallback<KADataService>() {
				@Override
				public void call(KADataService dataService) {
					dataService.getAPIAdapter().logout();
				}
			});
	    	return true;
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	private void setListForTopic(
			final Topic topic,
			final Class<? extends AbstractListFragment<?>> fragmentClass,
			final boolean forward) {
		
		this.requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				try {
					final Fragment frag = fragmentClass.newInstance();
					Bundle args = new Bundle();
					args.putString(PARAM_TOPIC_ID, topic.getId());
					frag.setArguments(args);
					
					// transition the fragments
					FragmentTransaction tx = getFragmentManager()
						.beginTransaction()
						.setBreadCrumbTitle(topic.getTitle());

					tx.replace(R.id.activity_home_list_container, frag, TAG_LIST_FRAGMENT)
						.commit();
					
				} catch (InstantiationException e) {
					// Swallow this; we know that both AbstractListFragment subclasses have zero-arg constructors.
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// Swallow this; we know that both AbstractListFragment subclasses are visible here.
					e.printStackTrace();
				}
			}
		});
		
		
	}


	@Override
	public void onRefreshRequested() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTopicSelected(final String readableId) {
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				Topic topic;
				try {
					topic = dataService.getHelper().getTopicDao().queryForId(readableId);
					String kind = topic.getChild_kind();
					
					// Should only ever find CHILD_KIND_TOPIC or CHILD_KIND_VIDEO here. TopicListFragment.addToQuery
					// should filter its query to ensure this is the case.
					Class<?> activityClass = Topic.CHILD_KIND_TOPIC.equals(kind)
							? TopicListActivity.class
							: VideoListActivity.class;
					
					launchListActivity(readableId, activityClass);
							
				} catch (SQLException e) {
					e.printStackTrace();
					return;
				}
			}
		});
		
	}
	
	private void launchListActivity(String topicId, Class<?> activityClass) {
		Intent intent = new Intent(this, activityClass);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		startActivity(intent);
	}
}
