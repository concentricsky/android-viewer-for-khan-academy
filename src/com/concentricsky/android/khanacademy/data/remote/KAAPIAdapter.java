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
package com.concentricsky.android.khanacademy.data.remote;

import static com.concentricsky.android.khanacademy.Constants.ACTION_BADGE_EARNED;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_BADGE;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.BadgeCategory;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.UserVideo;
import com.concentricsky.android.khanacademy.util.Log;
import com.j256.ormlite.dao.Dao;

public class KAAPIAdapter {
	
	public static final String LOG_TAG = KAAPIAdapter.class.getSimpleName();
	private final String consumerKey;
	private final String consumerSecret;
	private KADataService dataService;
	private User currentUser;
	
	public KAAPIAdapter(KADataService dataService, String consumerKey, String consumerSecret) {
		this.dataService = dataService;
		this.consumerKey = consumerKey;
		this.consumerSecret = consumerSecret;
	}
	
	public interface EntityCallback<T extends Object>  {
		public void call(T entity);
	}
	
	public interface UserLoginHandler {
		public void onUserLogin(User user);
	}
	
	/* ********************** User Update Listener ************************/
	private List<UserUpdateListener> listeners = new ArrayList<UserUpdateListener>();
	
	public interface UserUpdateListener {
		public void onUserUpdate(User user);
	}
	
	public void registerUserUpdateListener(UserUpdateListener l) {
		listeners.add(l);
	}
	
	public void unregisterUserUpdateListener(UserUpdateListener l) {
		listeners.remove(l);
	}
	
	private void doUserUpdate(User user) {
		for (UserUpdateListener l: listeners) {
			l.onUserUpdate(user);
		}
	}
	
	// Called by VideoProgressPostTask
	/*package*/ void doBadgeEarned(Badge badge) {
		Log.d(LOG_TAG, "doBadgeEarned");
		
		Intent intent = new Intent(ACTION_BADGE_EARNED);
		intent.putExtra(EXTRA_BADGE, badge);
		LocalBroadcastManager.getInstance(dataService).sendBroadcast(intent);
	}
	
	
	/**
	 * Call me from the UI thread, please.
	 * 
	 * Meant for use by broadcast receivers receiving ACTION_BADGE_EARNED.
	 * */
	public void toastBadge(Badge badge) {
		BadgeCategory category = badge.getCategory();
//		Dao<BadgeCategory, Integer> dao = dataService.getHelper().getDao(BadgeCategory.class);
//		dao.refresh(category);
		
		Toast toast = new Toast(dataService);
		View content = LayoutInflater.from(dataService).inflate(R.layout.badge, null, false);
		ImageView iconView = (ImageView) content.findViewById(R.id.badge_image);
		TextView pointsView = (TextView) content.findViewById(R.id.badge_points);
		TextView titleView = (TextView) content.findViewById(R.id.badge_title);
		TextView descView = (TextView) content.findViewById(R.id.badge_description);
		
		iconView.setImageResource(category.getIconResourceId());
		int points = badge.getPoints();
		if (points > 0) {
			pointsView.setText(points + "");
		} else {
			pointsView.setVisibility(View.GONE);
		}
		titleView.setText(badge.getDescription());
		descView.setText(badge.getSafe_extended_description());
		
		toast.setView(content);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setGravity(Gravity.TOP, 0, 200);
		toast.show();
	}
	
	public void testBadgeEarned() {
		Badge testBadge = new Badge();
		BadgeCategory testCategory = new BadgeCategory(1);
		testBadge.setPoints(500);
		testBadge.setDescription("Going Transonic");
		testBadge.setCategory(testCategory);
		testBadge.setSafe_extended_description("Quickly & correctly answer 10 exercise problems in a row (time limit depends on exercise difficulty)");
		doBadgeEarned(testBadge);
	}
	
	

	private String getCurrentUserId() {
		Log.d(LOG_TAG, "getCurrentUserId");
		String result = dataService.getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE)
				.getString(Constants.SETTING_USERID, null);
		Log.d(LOG_TAG, "    --> " + result == null ? "null" : result);
		return result;
	}
	
	/**
	 * Get the current user.
	 * 
	 * This will return the last user authenticated as long as they have not logged out. Specifically, when we launch
	 * the application, we do not explicitly check their credentials. Point totals, video progress, etc., will show
	 * until we make an explicit check (profile page or a video progress update).
	 * 
	 * Once we actually have to hit the api, if authentication fails, it will cause the current user to be set
	 * null and a user update will go out. At that point the entire app will behave as if the user is logged out.
	 * 
	 * @return the user or null.
	 */
	public User getCurrentUser() {
		if (currentUser == null) {
			// We may have just launched, and this isn't yet cached. Check for a non-null id in preferences.
			String userid = getCurrentUserId();
			if (userid == null) { // no user saved
				return null;
			}
		
			try {
				currentUser = dataService.getHelper().getUserDao().queryForId(userid);
			} catch (SQLException e) {
				// That's fine; pretend no user was logged in.
				e.printStackTrace();
			}
		}
		return currentUser;
	}
	
	
	public OAuthConsumer getConsumer(User user) {
		OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
		if (user != null) {
			String token = user.getToken();
			String secret = user.getSecret();
			if (token != null && secret != null) {
				consumer.setTokenWithSecret(token, secret);
			}
		}
		return consumer;
	}
	
	/**
	 * Set the current user id in shared preferences.  Usually, you'll want to call loginCurrentUser after this.
	 * 
	 * @param userid The user id to set.
	 */
	private void setCurrentUserId(String userid) {
		Log.d(LOG_TAG, "setCurrentUserId: " + userid);
		dataService.getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE)
				.edit()
				.putString(Constants.SETTING_USERID, userid)
				.apply();
	}
	
	private void setCurrentUser(User user) {
//		if (user == null) {
//			String url = "http://www.khanacademy.org/";
//			// TODO: Clear WebView cookies, so the "My Account" page is logged out as well.
//			// The idea is, use getCookie(url) and parse to get each cookie name, then setCookie with each "name=;"
//			// and possibly an expiration in the past.
//			CookieManager m = CookieManager.getInstance();
//			
//			// segfault (really?) on next line, unless CookieSyncManager.createInstance has been called.
//			String existing = m.getCookie(url);
//			// sample return value:
//			// fkey=1.0_FtAEhPDlC87EYQ==_1355510081; KAID="aHR0cDovL2dvb2dsZWlkLmtoYW5hY2FkZW15Lm9yZy8xMTgyMzkxMjIxOTM5MDEwNTQwNjUKMjAxMjM0OTE4MzQ1Ni45Mjk5MjAKODYyNGRmNzA5MGY1OGE2NzdiYjZlZTgxYTU4YjI4NmJmNTAwZTc2ZmNlNWE5MTkwYzNmYWZlNmY2MjMyMDVmMQ=="; gae_b_id=; GOOGAPPUID=23; return_visits_http%3A%2F%2Fgoogleid.khanacademy.org%2F118239122193901054065=1355510097.12996; __utma=3422703.2067802560.1355510099.1355510099.1355510099.1; __utmb=3422703.3.10.1355510099; __utmc=3422703; __utmz=3422703.1355510099.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); __utmv=3422703.|1=User%20Type=Logged%20In=1^2=User%20Points=17300=1^3=User%20Videos=18=1^4=User%20Exercises=0=1
//			Log.d(LOG_TAG, existing);
//			
//			m.setCookie(url, "");
//			
//			// Naive solution, but this also clears facebook/google cookies, etc.
//			m.removeAllCookie();
//		}
		currentUser = user;
		setCurrentUserId(user == null ? null : user.getNickname());
	}

	
	public void login(final String token, final String secret, final UserLoginHandler handler) {
		OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
		consumer.setTokenWithSecret(token, secret);
		fetchUser(consumer, new EntityCallback<User>() {
			@Override
			public void call(User returnedUser) {
				
				if (returnedUser != null) {
					returnedUser.setToken(token);
					returnedUser.setSecret(secret);
					try {
						Dao<User, String> userDao = dataService.getHelper().getUserDao();
						if (userDao.getConnectionSource().isOpen()) {
							userDao.createOrUpdate(returnedUser);
						}
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				handler.onUserLogin(returnedUser);
				currentUser = returnedUser;
				setCurrentUser(returnedUser);
				doUserUpdate(returnedUser);
			}
		});
	}
	
	/**
	 * Log the current user out.
	 * 
	 * @return true if a user was logged in, false otherwise.
	 */
	public boolean logout() {
		boolean result = this.isAuthenticated();
		setCurrentUser(null);
		doUserUpdate(null);
		Toast.makeText(dataService, dataService.getString(R.string.msg_logged_out),
				Toast.LENGTH_SHORT).show();
		return result;
	}
	
	/**
	 * Do we have an authenticated user registered?
	 * 
	 * Does not perform a request to validate the authentication; just returns
	 * true if we have what looks to be a valid consumer.
	 * 
	 * @return true if a user is logged in, false otherwise.
	 */
	public boolean isAuthenticated() {
		return this.currentUser != null;
	}
	
//  /api/v1/user
	/**
	 * Fetch data for the currently logged-in user. Requires prior authentication.
	 * 
	 * @param callback
	 */
	public void fetchUser(final OAuthConsumer consumer, final EntityCallback<User> callback) {
		String url = "http://www.khanacademy.org/api/v1/user";
		new KAEntityFetcherTask<User>(url, consumer) {
			@Override
			protected void onPostExecute(User result) {
				callback.call(result);
			}
		}.execute();
	}
	
	
	public void requestUserVideoUpdate(final User user) {
		fetchUserVideos(getConsumer(user), new EntityCallback<List<UserVideo>>() {
			@Override
			public void call(List<UserVideo> userVideos) {
				int numChanged = 0;
				if (userVideos == null) {
					// TODO
				}
				
				Dao<UserVideo, Integer> userVideoDao = null;
				try {
					userVideoDao = dataService.getHelper().getUserVideoDao();
				} catch (SQLException e) {
					// Without a Dao we won't be able to do any updates, but probably shouldn't crash just yet.
					e.printStackTrace();
					return;
				}
				
				Map<String, Object> values = new HashMap<String, Object>();
				for (UserVideo v : userVideos) {
					// TODO : is it important to remove old userVideos that don't appear in this response?
					values.put("user_id", v.getUser().getNickname());
					values.put("video_id", v.getVideo_id());
					try {
						// createOrUpdate would be slick here, but since ORMLite does not support
						// multi-valued primary keys we can't key on User + Video
						List<UserVideo> existing = userVideoDao.queryForFieldValues(values);
						if (existing.size() > 0) {
							UserVideo it = existing.get(0);
							// the newly downloaded video v is most current.
							v.setId(it.getId());
							userVideoDao.update(v);
						} else {
							userVideoDao.create(v);
						}
						numChanged++;
					} catch (SQLException e) {
						// Can't update this video, but no need to crash.
						e.printStackTrace();
					} catch (IllegalStateException e) {
						// Can occur when a video update is coming back while we shut down the app, if the db is already closed.
						// TODO : the real fix is to use startService for this task.
						e.printStackTrace();
					}
				}
				
				if (numChanged > 0) {
					// This will trigger the video fragments to refresh the UserVideo related to this User and their current video.
					doUserUpdate(user);
				}
			}
		});
	}
	
//  /api/v1/user/videos
	public void fetchUserVideos(final OAuthConsumer consumer, final EntityCallback<List<UserVideo>> callback) {
		String url = "http://www.khanacademy.org/api/v1/user/videos";
		new KAEntityCollectionFetcherTask<UserVideo>(UserVideo.class, url, consumer) {
			@Override
			protected void onPostExecute(List<UserVideo> result) {
				if (result == null) {
					exception.printStackTrace();
					// But don't crash.
				}
				callback.call(result);
			}
		}.execute();
	}
	
	public void postVideoProgress(final UserVideo userVideo, final Runnable successHandler, final Runnable errorHandler) {
		Log.d(LOG_TAG, "postVideoProgress");
		
		new VideoProgressPostTask(dataService) {
			@Override
			protected void onPostExecute(User returnedUser) {
				// if returnedUser is null, the post failed.
				if (returnedUser != null) {
					doUserUpdate(returnedUser);
					if (successHandler != null) {
						successHandler.run();
					}
				} else {
					if (errorHandler != null) {
						errorHandler.run();
					}
				}
			}
		}.execute(userVideo);
	}
	
}
