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

import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_ID;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings.ZoomDensity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.data.remote.KAAPIAdapter;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

/**
 * This just displays the profile page as loaded from Khan servers.
 * 
 * @author austinlally
 *
 */
public class ShowProfileActivity extends KADataServiceProviderActivityBase {
	//TODO done button
	public static final String LOG_TAG = ShowProfileActivity.class.getSimpleName();
	
	private static final int WEBVIEW_LOAD_TIMEOUT = 5000;

	private View spinnerView;
	private WebView webView;
	private Handler handler = new Handler();
	private KAAPIAdapter api;
	private KADataService dataService;
	
	private boolean destroyed = false;

	/**
	 * Get the current user, and fail if there is none.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		destroyed = false;
		this.webViewTimeoutPromptDialog = new AlertDialog.Builder(this)
	        .setMessage("The page is taking a long time to respond. Stop loading?")
	        .setPositiveButton("Stop", new DialogInterface.OnClickListener() {
	        	@Override
	            public void onClick(DialogInterface dialog, int id) {
	        		if (webView != null) {
	        			webView.stopLoading();
	        			finish();
	        		}
	            }
	        })
	        .setNegativeButton("Wait", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					stopWebViewLoadTimeout();
				}
			})
	        .setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					startWebViewLoadTimeout();
				}
			}).create();
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		
		setContentView(R.layout.profile);
		setTitle(getString(R.string.profile_title));
		webView = (WebView) findViewById(R.id.web_view);
		webView.setMinimumWidth(800);
		enableJavascript(webView);
		webView.getSettings().setDefaultZoom(ZoomDensity.FAR);
		webView.setWebViewClient(new WebViewClient() {
			@Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
				Log.d(LOG_TAG, "shouldOverrideUrlLoading: " + url);
				URL parsed = null;
				try {
					parsed = new URL(url);
				} catch (MalformedURLException e) {
					// Let the webview figure that one out.
					return false;
				}
				
				// Only ka links will load in this webview.
				if (parsed.getHost().equals("www.khanacademy.org")) {
					
					// Video urls should link into video detail. See below for another video url format.
					if (parsed.getPath().equals("/video")) {
						String query = parsed.getQuery();
						if (query != null && query.length() > 0) {
							String[] items = query.split("&");
							String videoId = null;
							for (String item : items) {
								String[] parts = item.split("=", 2);
								if (parts.length > 1) {
									if ("v".equals(parts[0])) {
										videoId = parts[1];
										break;
									}
								}
							}
							if (videoId != null) {
								String[] ids = normalizeVideoAndTopicId(videoId, "");
								if (ids != null) {
									launchVideoDetailActivity(ids[0], ids[1]);
									return true;
								}
							}
						}
						// There was no ?v= or something weird is going on. Allow the page
						// load, which should hit KA's nice "no video found" page.
						showSpinner();
						startWebViewLoadTimeout();
						return false;
					}
					
					if (parsed.getPath().startsWith("/profile")) {
						// navigation within the profile makes sense here.
						showSpinner();
						startWebViewLoadTimeout();
						return false;
					}
					
					// Embedded logout option (in upper left menu) can be intercepted and cause the app to be logged out as well.
					if (parsed.getPath().equals("/logout")) {
						if (dataService != null) {
							dataService.getAPIAdapter().logout();
						}
						finish();
						return false; // try to let the webview hit logout to get the cookies cleared as we exit
					}
					
					// There is a new kind of video url now.. thanks guys..
					// http://www.khanacademy.org/video/subtraction-2
					//  redirects to
					// http://www.khanacademy.org/math/arithmetic/addition-subtraction/two_dig_add_sub/v/subtraction-2
					String[] path = parsed.getPath().split("/");
					List<String> parts = Arrays.asList(path);
					if (parts.contains("v")) {
						String videoId = null;
						String topicId = null;
						for (int i=path.length-1; i>=0; --i) {
							if (path[i].equals("v")) {
								continue;
							}
							if (videoId == null) {
								videoId = path[i];
							} else if (topicId == null) {
								topicId = path[i];
							} else {
								break;
							}
						}
						
						if (videoId != null && topicId != null && dataService != null) {
							// Looks like a video url. Double check that we have the topic and video before launching detail activity.
							Log.d(LOG_TAG, "video and topic ids found; looks like a video url.");
							
							String[] ids = normalizeVideoAndTopicId(videoId, topicId);
							if (ids != null) {
								launchVideoDetailActivity(ids[0], ids[1]);
								return true;
							}
						}
					} else if (parsed.getPath().startsWith("/video")) {
						String videoId = path[path.length-1];
						String[] ids = normalizeVideoAndTopicId(videoId, "");
						if (ids != null) {
							launchVideoDetailActivity(ids[0], ids[1]);
							return true;
						}
					}
						
//					showSpinner();
//					startWebViewLoadTimeout();
//					return false;
				}
					
				// All other urls should launch in the browser instead of here, except hash changes if we can distinguish.
				loadInBrowser(url);
				return true;
			}
			@Override public void onPageFinished(WebView view, String url) {
				Log.d(LOG_TAG, "onPageFinished");
				stopWebViewLoadTimeout();
				if (!destroyed) {
					hideSpinner();
				}
			}
			@Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
				Log.d(LOG_TAG, "onPageStarted");
				stopWebViewLoadTimeout();
				
//				view.loadUrl("javascript:document.addEventListener( 'DOMContentLoaded',function() {AndroidApplication.jqueryReady()} );");

//				handler.postDelayed(new Runnable() {
//					@Override
//					public void run() {
//						if (!destroyed) {
//							hijack();
//						}
//					}
//				}, 100);
			}
		});
//		webView.addJavascriptInterface(new Object() {
//			public void log(String msg) {
//				Log.d(LOG_TAG, "JSLOG: " + msg);
//			}
//			public void jqueryReady() {
//				Log.d(LOG_TAG, "javascript: jqueryReady");
//				hijack();
//			}
//		}, "AndroidApplication");
//		
		showSpinner();

		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService service) {
				dataService = service;
				api = service.getAPIAdapter();
				
				String[] credentials = getCurrentLoginCredentials();
				loginUser(credentials[0], credentials[1]);
			}
		});
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(WebView webView) {
		webView.getSettings().setJavaScriptEnabled(true);
	}
	
	private String[] normalizeVideoAndTopicId(String videoId, String topicId) {
		Log.d(LOG_TAG, "videoAndTopicExist: " + videoId + ", " + topicId);
		if (videoId != null && topicId != null && dataService != null) {
			Dao<Video, String> videoDao;
			Dao<Topic, String> topicDao;
			try {
				videoDao = dataService.getHelper().getVideoDao();
				QueryBuilder<Video, String> q = videoDao.queryBuilder();
				q.where().eq("youtube_id", videoId).or().eq("readable_id", videoId);
				Video video = videoDao.queryForFirst(q.prepare());
				if (video != null) {
					Log.d(LOG_TAG, " video found");
					topicDao = dataService.getHelper().getTopicDao();
					Topic parent = topicDao.queryForId(topicId);
					if (parent != null) {
						Log.d(LOG_TAG, " topic found");
						return new String[] {video.getReadable_id(), parent.getId()};
					} else {
						parent = topicDao.queryRaw(
								"select topic._id from topic, topicvideo where topicvideo.video_id=? and topicvideo.topic_id = topic._id limit 1", 
								topicDao.getRawRowMapper(), new String[] {video.getReadable_id()}).getFirstResult();
						if (parent != null) {
							Log.d(LOG_TAG, " another topic found");
							return new String[] {video.getReadable_id(), parent.getId()};
						}
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	private void launchVideoDetailActivity(String videoId, String topicId) {
		Intent intent = new Intent(this, VideoDetailActivity.class);
		intent.putExtra(PARAM_VIDEO_ID, videoId);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		startActivity(intent);
	}
	
	private void loadInBrowser(String url) {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
	}
	
	private Runnable timeoutPromptRunnable = new Runnable() {
		@Override
		public void run() {
			promptLoadTimeout();
		}
	};
	
	private AlertDialog webViewTimeoutPromptDialog;
	
	private void startWebViewLoadTimeout() {
		stopWebViewLoadTimeout();
		handler.postDelayed(timeoutPromptRunnable, WEBVIEW_LOAD_TIMEOUT);
	}
	
	private void stopWebViewLoadTimeout() {
		handler.removeCallbacks(timeoutPromptRunnable);
	}
	
	private void promptLoadTimeout() {
		stopWebViewLoadTimeout();
		Log.d(LOG_TAG, "showing dialog");
		webViewTimeoutPromptDialog.show();
	}
	
	@Override
	protected void onDestroy() {
		destroyed = true;
		if (webView != null) {
			webView.destroy();
			webView = null;
		}
		super.onDestroy();
	}
	
	private KAAPIAdapter.UserLoginHandler userLoginHandler = new KAAPIAdapter.UserLoginHandler() {
		@Override
		public void onUserLogin(final User user) {
			if (destroyed) return;
			
			if (user == null) {
				// No user logged in, likely thanks to bad credentials.
				launchSignInActivity();
				
				// TODO
				// Could also be network trouble.
				
			} else {
				// Success.
				requestDataService(new ObjectCallback<KADataService>() {
					@Override
					public void call(KADataService dataService) {
						dataService.getAPIAdapter().requestUserVideoUpdate(user);
					}
				});
				loadProfilePage(user);
			}
		}
	};
	private void launchSignInActivity() {
		Intent intent = new Intent(this, SignInActivity.class); 
		startActivityForResult(intent, Constants.REQUEST_CODE_USER_LOGIN);
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.d(LOG_TAG, "onActivityResult");
		switch (requestCode) {
		case Constants.REQUEST_CODE_USER_LOGIN:
			handleLoginResult(resultCode, intent);
			break;
		default:
			// ???
			break;
		}
	}
	private void handleLoginResult(int resultCode, Intent intent) {
		Log.d(LOG_TAG, "request code was user login, result code is " + resultCode);
		if (destroyed) return;
		
		// Sent by SignInActivity
		switch (resultCode) {
		case Constants.RESULT_CODE_SUCCESS:
			String token = intent == null ? null : intent.getStringExtra(Constants.PARAM_OAUTH_TOKEN);
			String secret = intent == null ? null : intent.getStringExtra(Constants.PARAM_OAUTH_SECRET);
			
			loginUser(token, secret);
			
			break;
		case Constants.RESULT_CODE_FAILURE:
			// Network error, user declines authorization, activity closed before finishing.
			Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
			// fall through
		default:
			// User exits the login dialog without finishing the process.
			finish();
		}
		
	}
	
	
	
	// Begin the login process here. We try to log in with whatever token,secret we have saved.
	// We will get a callback from the API Adapter after this with a user object. If the user
	// isn't null, then we're logged in. If it is, that indicates we need to launch the 
	// SignInActivity to get a new set of credentials and try again.
	private void loginUser(final String token, final String secret) {
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				dataService.getAPIAdapter().login(token, secret, userLoginHandler);
			}
		});
	}
	
	private String[] getCurrentLoginCredentials() {
		Log.d(LOG_TAG, "getCurrentLoginCredentials");
		// Make sure this.api is non-null before trying this.
		User user = api.getCurrentUser();
		String[] result = new String[2];
		if (user != null) {
			result[0] = user.getToken();
			result[1] = user.getSecret();
		} else {
			result[0] = "";
			result[1] = "";
		}
		Log.d(LOG_TAG, String.format(" --> [%s, %s]", result[0], result[1]));
		return result;
	}
		
	private void loadProfilePage(User user) {
		String url = "http://www.khanacademy.org/api/auth/token_to_session?continue=";
		try {
			url += URLEncoder.encode("/profile", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			url += "/profile"; // Works ok as of 03/22/2013, but encoding it is of course the right way.
		}
		
		HashMap<String, String> headers = new HashMap<String, String>();
		
		HttpGet request = new HttpGet(url);
		try {
			api.getConsumer(user).sign(request);
			for (Header h : request.getAllHeaders()) {
				headers.put(h.getName(), h.getValue());
			}
		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
		}
		
		webView.loadUrl(url, headers);
		
	}

	/**
	 * Show a loading indicator.
	 */
	protected void showSpinner() {
		if (spinnerView == null) {
			spinnerView = getLayoutInflater().inflate(R.layout.spinner, null, false);
		}
		handler.post(new Runnable() {
			@Override
			public void run() {
				ViewGroup parent = (ViewGroup) spinnerView.getParent();
				if (parent != null) parent.removeView(spinnerView);
				((FrameLayout) findViewById(R.id.popover_view)).addView(spinnerView);
			}
		});
	}
	
	/**
	 * Hide any loading indicator.
	 */
	protected void hideSpinner() {
		if (spinnerView != null) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					ViewGroup parent = (ViewGroup) spinnerView.getParent();
					if (parent != null) parent.removeView(spinnerView);
				}
			});
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if (webView != null && webView.canGoBack()) {
			webView.goBack();
		} else {
			super.onBackPressed();
		}
	}
}
