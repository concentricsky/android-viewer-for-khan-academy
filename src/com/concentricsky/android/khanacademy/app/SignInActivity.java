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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;

/**
 * We need a token from khanacademy.org, so we load the authentication page from there. That page
 * redirects the user to Google or Facebook and asks them to authenticate. Google or Facebook calls
 * back to khanacademy.org with an authenticated token. At this point, we receive a callback with
 * an authenticated request token from khanacademy, which we then exchange for an access token.
 * The access token is long-lived, and we store it to log back in automatically on subsequent runs.
 * 
 * @author austinlally
 *
 */
public class SignInActivity extends KADataServiceProviderActivityBase {
	
	public static final String LOG_TAG = SignInActivity.class.getSimpleName();

	private static final String KHAN_API_URL = "http://www.khanacademy.org/api";
	private static final String OAUTH_REQUEST_URL = KHAN_API_URL + "/auth/request_token";
	private static final String OAUTH_ACCESS_TOKEN_URL = KHAN_API_URL + "/auth/access_token";
	private static final String OAUTH_CALLBACK_URL = "khan-oauth:///";
	
	public static final String ACTION_OAUTH = "com.concentricsky.android.khanacademy.ACTION_OAUTH";
	
	private OAuthConsumer consumer;
	
	@SuppressWarnings("rawtypes")
	private List<AsyncTask> currentTasks = new ArrayList<AsyncTask>();
	private View spinnerView;
	private Handler handler = new Handler();
	private WebView webView;
	
	/**
	 * Set up a url filter to catch our callback, then start a request token operation.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		setTitle("Log in");
		
		// Show no activity transition animation; we want to appear as if we're the same activity as the underlying ShowProfileActivity.
		overridePendingTransition(0, 0);
		setContentView(R.layout.popover_web);
		
		webView = (WebView) findViewById(R.id.web_view);
		enableJavascript(webView);
		
		webView.setWebViewClient(new WebViewClient() {
			@Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
				showSpinner();
				Log.d(LOG_TAG, ">>>>>>>>>>>>> url: " + url);
				if (url.contains(OAUTH_CALLBACK_URL)) {
					currentTasks.add(new AccessTokenFetcher().execute(url));
					return true;
				}
				
				return false;
			}
			@Override public void onPageFinished(WebView view, String url) {
				hideSpinner();
			}
		});
		showSpinner();

		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService service) {
				consumer = service.getAPIAdapter().getConsumer(null);
				currentTasks.add(new RequestTokenFetcher().execute());
			}
		});
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(WebView webView) {
		webView.getSettings().setJavaScriptEnabled(true);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.sign_in, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.register:
			// Launch KA's registration page in the browser.
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_register))));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Cancel tasks on pause. 
	 */
	@Override
	protected void onPause() {
		super.onPause();
		// Show no activity transition animation; we want to appear as if we're the same activity as the underlying ShowProfileActivity.
		overridePendingTransition(0, 0);
		
		for (@SuppressWarnings("rawtypes") AsyncTask t : currentTasks) {
			t.cancel(true);
		}
	}
	
	/**
	 * Signs a request with our consumer token and consumer secret, and loads
	 * khanacademy's authentication page with it.
	 * 
	 * @author austinlally
	 */
	private class RequestTokenFetcher extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			String requestUrl = OAUTH_REQUEST_URL;
			requestUrl += "?view=mobile&oauth_callback=" + OAUTH_CALLBACK_URL;
			final HttpGet request = new HttpGet(requestUrl);
					
			try {
				synchronized (consumer) {
					consumer.sign(request);
				}
				
				final Map<String, String> headers = new HashMap<String, String>();
				Log.d(LOG_TAG, "request line: " + request.getRequestLine().toString());
				for (Header h : request.getAllHeaders()) {
					Log.d(LOG_TAG, h.getName() + ": " + h.getValue());
					headers.put(h.getName(), h.getValue());
				}
				handler.post(new Runnable() {
					@Override
					public void run() {
						try {
							webView.loadUrl(request.getURI().toURL().toString(), headers);
						} catch (MalformedURLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});

			} catch (OAuthMessageSignerException e1) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e1.printStackTrace();
			} catch (OAuthExpectationFailedException e1) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e1.printStackTrace();
			} catch (OAuthCommunicationException e1) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e1.printStackTrace();
			}
			return null;
		}
		
		@Override
		public void onPostExecute(Void result) {
			currentTasks.remove(this);
		}
		
	}
	
	/**
	 * Given our callback url with oauth-related query string containing an 
	 * authenticated request token, secret, and verifier, as received from khan, 
	 * this task requests an access token and then launches a user info task and 
	 * a user videos task.
	 * 
	 * @author austinlally
	 *
	 */
	private class AccessTokenFetcher extends AsyncTask<String, Void, String[]> {

		@Override
		protected String[] doInBackground(String... params) {
			String url = params[0];
			UrlQuerySanitizer sanitizer = new UrlQuerySanitizer();
			String[] parameters = new String[] {
					OAuth.OAUTH_TOKEN, OAuth.OAUTH_TOKEN_SECRET, OAuth.OAUTH_VERIFIER
			};
			UrlQuerySanitizer.ValueSanitizer valueSanitizer = new UrlQuerySanitizer.ValueSanitizer() {
				@Override
				public String sanitize(String value) {
					return value;
				}
			};
			sanitizer.registerParameters(parameters, valueSanitizer);
			sanitizer.parseUrl(url);

			synchronized (consumer) {
				consumer.setTokenWithSecret(sanitizer.getValue(OAuth.OAUTH_TOKEN), sanitizer.getValue(OAuth.OAUTH_TOKEN_SECRET));
			}

			OAuthProvider provider = new CommonsHttpOAuthProvider(null, OAUTH_ACCESS_TOKEN_URL, null);
			//since we aren't using provider.retrieveRequestToken, this isn't set for us
			provider.setOAuth10a(true);
			
			Log.d(LOG_TAG, "endpoint " + provider.getAccessTokenEndpointUrl());
			Log.d(LOG_TAG, "setting token " + sanitizer.getValue(OAuth.OAUTH_TOKEN) + " and secret " + sanitizer.getValue(OAuth.OAUTH_TOKEN_SECRET) + " and verifier " + sanitizer.getValue(OAuth.OAUTH_VERIFIER));
			try {
				provider.retrieveAccessToken(consumer, sanitizer.getValue(OAuth.OAUTH_VERIFIER));
			} catch (OAuthMessageSignerException e) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e.printStackTrace();
			} catch (OAuthNotAuthorizedException e) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e.printStackTrace();
			} catch (OAuthExpectationFailedException e) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e.printStackTrace();
			} catch (OAuthCommunicationException e) {
				setResult(Constants.RESULT_CODE_FAILURE);
				e.printStackTrace();
			}
			
			Log.d(LOG_TAG, "now token is " + consumer.getToken() + " and secret is " + consumer.getTokenSecret());
			
			// Consider ourselves logged in.
			return new String[] {consumer.getToken(), consumer.getTokenSecret()};
		}
		
		@Override
		public void onPostExecute(String[] result) {
			currentTasks.remove(this);
			
			Intent resultIntent = new Intent();
			resultIntent.putExtra(Constants.PARAM_OAUTH_TOKEN, result[0]);
			resultIntent.putExtra(Constants.PARAM_OAUTH_SECRET, result[1]);
			SignInActivity.this.setResult(Constants.RESULT_CODE_SUCCESS, resultIntent);
			finish();
		}
		
	}
	
	/**
	 * Show a loading indicator.
	 */
	protected void showSpinner() {
		if (spinnerView == null) {
			spinnerView = getLayoutInflater().inflate(R.layout.spinner, null, false);
		}
		handler.post(new Runnable() {
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
				public void run() {
					ViewGroup parent = (ViewGroup) spinnerView.getParent();
					if (parent != null) parent.removeView(spinnerView);
				}
			});
		}
	}


}
