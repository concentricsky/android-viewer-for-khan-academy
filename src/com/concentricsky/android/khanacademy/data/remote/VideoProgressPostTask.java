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

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.AsyncTask;

import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.UserVideo;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.util.Log;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.j256.ormlite.dao.Dao;

class VideoProgressPostTask extends AsyncTask<UserVideo, Void, User> {
	
	public static final String LOG_TAG = VideoProgressPostTask.class.getSimpleName();
	
	private String url = "http://www.khanacademy.org/api/v1/user/videos/%s/log";
	private KADataService dataService;
	private OAuthConsumer consumer;
	
	public VideoProgressPostTask(KADataService dataService) {
		this.dataService = dataService;
		
		
	}
	
	@Override
	protected User doInBackground(UserVideo... params) {
		
		/*
		 * 
		 * A Giant waste of time deserves a Giant comment block.
		 * 
		 * To properly sign post requests, we need to send the payload as a querystring.
		 * 
		 * Some alternatives that did not work:
		 *   Set the data as params onto the request
		 *   Set the data as params onto the OAuthConsumer
		 *   Set the data onto the request as headers (?!)
		 *   Write the data into the request entity
		 *   
		 * Tried all of those before and after signing, and with various Accept / Content-Type headers.
		 * 
		 * This work-around comes from a comment in this bug report related to HttpURLConnection:
		 *   http://code.google.com/p/oauth-signpost/issues/detail?id=15
		 *   
		 * According to the other comments, this should not be necessary when using the apache http 
		 * library, as we do get a chance to sign the request after specifying content but before 
		 * opening the connection.  However, I had no luck.
		 * 
		 */
		UserVideo userVideo = params[0];
		String videoId = null;
		User user = null;
		Dao<User, String> userDao = null;
		Dao<Video, String> videoDao = null;
		try {
			user = userVideo.getUser();
			userDao = dataService.getHelper().getUserDao();
			userDao.refresh(user);
			consumer = dataService.getAPIAdapter().getConsumer(user);
			
			videoDao = dataService.getHelper().getVideoDao();
			Video video = videoDao.queryForFirst(videoDao.queryBuilder().where().eq("readable_id", userVideo.getVideo_id()).prepare());
			
			videoId = video.getYoutube_id();
			url = String.format(url, videoId);
		} catch (SQLException e) {
			// Fail silently when trying to post progress updates.
			e.printStackTrace();
			return null;
		}
		
		final User existingUser = user;
		final UserVideo existingUserVideo = userVideo;
		VideoProgressUpdate payload = new VideoProgressUpdate(userVideo);
		
		VideoProgressResult result = remoteFetch(payload);
		
		if (result != null) {
			ActionResults results = result.getAction_results();
			// Update User and UserVideo, save, and fire callbacks.
			
			// User.
			User returnedUser = results.getUser_data();
			
			// The returned user object is more current than the existing (total seconds watched at the least).
			// Set onto the new user the fields that won't appear in the response. Nickname (and hence id) is already correct.
			returnedUser.setToken(existingUser.getToken());
			returnedUser.setSecret(existingUser.getSecret());
			
			try {
				dataService.getHelper().getUserDao().update(returnedUser);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			// Badges.
			try {
				List<Badge> badges = results.getBadges_earned().getBadges();
				
				// DEBUG
//				for (Badge b : badges) {
//					try {
//						Log.d(LOG_TAG, new ObjectMapper().writeValueAsString(b));
//					} catch (JsonProcessingException e) {
//						e.printStackTrace();
//					}
//				}
				
				//  It's possible to get a response with multiple earned badges. In fact, this always happens 
				// when the user earns "Awesome Listener" for watching an hour of a topic; the user also earns
				// a "Great Listener" for 30 minutes and a "Nice Listener" for 15 minutes at the same time.
				//  We don't want a 10-minute badge-toaststravaganza, so just toast the last one listed.
				if (badges != null && badges.size() > 0) {
					Badge b = badges.get(badges.size() - 1);
					dataService.getAPIAdapter().doBadgeEarned(b);
				}
			} catch (NullPointerException e) {
				// No badges were earned.
			}
			
			// Now UserVideo.
			UserVideo returnedVideo = results.getUser_video();
			
			// Again, the returned object is fresher than our existing one.
			returnedVideo.setId(existingUserVideo.getId());
			try {
				dataService.getHelper().getUserVideoDao().update(returnedVideo);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			// Do the user update here, after the UserVideo has been saved.
			return returnedUser;
		} else {
			Log.e(LOG_TAG, "null result in postVideoProgress");
			return null;
		}
	}
	
	private VideoProgressResult remoteFetch(VideoProgressUpdate update) {
        VideoProgressResult result = null;
		
        String q = String.format(Locale.US, "last_second_watched=%d&seconds_watched=%d", update.getLast_second_watched(), update.getSeconds_watched());
        url = String.format("%s?%s", url, q);
        Log.d(KAAPIAdapter.LOG_TAG, "posting video progress: " + url);
        
        // Use this! The response is chunked, so don't try to get the Content-Length and read a buffer of that length.
        // However, the response is small, so it isn't a big deal that this handler blocks until it's done.
        ResponseHandler<String> h = new BasicResponseHandler();
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost request = new HttpPost(url);
        ObjectMapper mapper = new ObjectMapper();
        
		try {
	        consumer.sign(request);
	        
            String response = httpClient.execute(request, h);
            
            result = mapper.readValue(response, VideoProgressResult.class);
            
            // DEBUG
//            User ud = result.action_results.user_data;
//            UserVideo uv = result.action_results.user_video;
//            if (result.action_results.badges_earned != null) {
//	            List<Badge> badges = result.action_results.badges_earned.getBadges();
//	            if (badges != null) {
//	            	Log.d(KAAPIAdapter.LOG_TAG, "Badges: ");
//	            	for (Badge b : badges) {
//	            		Log.d(KAAPIAdapter.LOG_TAG, "     " + b.getDescription() + "  (" + b.getPoints() + " points)");
//	            	}
//	            } else {
//		            Log.d(KAAPIAdapter.LOG_TAG, "badges was null");
//	            }
//            } else {
//	            Log.d(KAAPIAdapter.LOG_TAG, "badges was null");
//            }
//            
//            Log.d(KAAPIAdapter.LOG_TAG, "url was " + url);
//            Log.d(KAAPIAdapter.LOG_TAG, "got data for " + ud.getNickname() + ", video points: " + uv.getPoints());
	        
            
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
		} catch (HttpResponseException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
		return result;
	}
	
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class VideoProgressUpdate {
		int last_second_watched;
		int seconds_watched;
		
		public VideoProgressUpdate(UserVideo userVideo) {
			last_second_watched = userVideo.getLast_second_watched();
			seconds_watched = userVideo.getSeconds_watched();
		}
		
		/**
		 * @return the last_second_watched
		 */
		public int getLast_second_watched() {
			return last_second_watched;
		}
		/**
		 * @param last_second_watched the last_second_watched to set
		 */
		public void setLast_second_watched(int last_second_watched) {
			this.last_second_watched = last_second_watched;
		}
		/**
		 * @return the seconds_watched
		 */
		public int getSeconds_watched() {
			return seconds_watched;
		}
		/**
		 * @param seconds_watched the seconds_watched to set
		 */
		public void setSeconds_watched(int seconds_watched) {
			this.seconds_watched = seconds_watched;
		}
	}
	
	// This came as 'action_results': {... 'badges_earned': { 'badges': [ /* in here */ ] } ... } .
	/**
	 * When ActionResults contain some badges earned, they are wrapped in this object.
	 */
	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class BadgesEarned {
		List<Badge> badges;
		public List<Badge> getBadges() { return badges; }
		public void setBadges(List<Badge> badges) { this.badges = badges; }
	}
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class ActionResults {
		
		User user_data;
		UserVideo user_video;
		BadgesEarned badges_earned;
		
		public User getUser_data() { return user_data; }
		public void setUser_data(User user_data) { this.user_data = user_data; }
		
		public UserVideo getUser_video() { return user_video; }
		public void setUser_video(UserVideo user_video) { this.user_video = user_video; }
		
		public BadgesEarned getBadges_earned() { return badges_earned; }
		public void setBadges_earned(BadgesEarned badges_earned) { this.badges_earned = badges_earned; }
		
		// tutorial_node_progress
		// user_info_html
	}
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class VideoProgressResult {
		ActionResults action_results;
		public ActionResults getAction_results() {
			return action_results;
		}
		public void setAction_results(ActionResults action_results) {
			this.action_results = action_results;
		}
		
		// time_watched
	}
	
}
