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
import static com.concentricsky.android.khanacademy.Constants.ACTION_DOWNLOAD_PROGRESS_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.ACTION_OFFLINE_VIDEO_SET_CHANGED;
import static com.concentricsky.android.khanacademy.Constants.ACTION_TOAST;
import static com.concentricsky.android.khanacademy.Constants.DEFAULT_VIDEO_ID;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_BADGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_MESSAGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_STATUS;
import static com.concentricsky.android.khanacademy.Constants.PARAM_PROGRESS_DONE;
import static com.concentricsky.android.khanacademy.Constants.PARAM_PROGRESS_UNKNOWN;
import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_USERVIDEO_POINTS;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_PLAY_STATE;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_POSITION;
import static com.concentricsky.android.khanacademy.Constants.TAG_CAPTION_FRAGMENT;
import static com.concentricsky.android.khanacademy.Constants.TAG_VIDEO_FRAGMENT;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Time;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.MainMenuDelegate;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.KADataService.ServiceUnavailableException;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.UserVideo;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.data.remote.KAAPIAdapter;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.util.OfflineVideoManager;
import com.concentricsky.android.khanacademy.views.ThumbnailWrapper;
import com.concentricsky.android.khanacademy.views.VideoController;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RawRowMapper;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;

public class VideoDetailActivity extends KADataServiceProviderActivityBase
		implements VideoFragment.Callbacks, CaptionFragment.Callbacks {
	
	public static final String LOG_TAG = VideoDetailActivity.class.getSimpleName();
	private static final int POINTS_GONE = -1;
	private static final int NAV_HIDE_DELAY = 3000; // ms
	
	private String videoId;
	private String topicId;
	private Video video;
	private UserVideo userVideo;
	
	private View headerView;
	private TextView pointsView;
	
	private VideoFragment videoFragment;
	private CaptionFragment captionFragment;
	
	private MainMenuDelegate mainMenuDelegate;
	private Menu mainMenu;
	
	private KADataService dataService;
	private ShareActionProvider shareActionProvider;
	
	private int currentOrientation;
	private boolean navVis = true;
	private boolean videoIsDownloaded = false;
	private boolean isFullscreen;
	private long downTime;
	private String nextVideoId;
	private boolean shouldShowVideoControls;
	private boolean isBigScreen;
	
	private Time rightNow = new Time();
	// As of 12/17/12, looks like most of these values are unnecessary. Point gain appears to be limited server side,
	// so even if the user skips ahead they will not be granted too many points.
	private int lastPost; // in seconds
	private float percentLastSaved;
	private boolean saving = false;
	
	private int desiredSeekPosition;
	private boolean isVideoPlayerPrepared;
	
	private Handler handler = new Handler();
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_BADGE_EARNED.equals(intent.getAction()) && dataService != null) {
				Badge badge = (Badge) intent.getSerializableExtra(EXTRA_BADGE);
				dataService.getAPIAdapter().toastBadge(badge);
			} else if (ACTION_DOWNLOAD_PROGRESS_UPDATE.equals(intent.getAction())) {
				
				@SuppressWarnings("unchecked")
				final Map<String, Integer> youtubeIdToPct = (Map<String, Integer>) intent.getSerializableExtra(EXTRA_STATUS);
				
				if (video != null) {
					Integer pct = youtubeIdToPct.get(video.getYoutube_id());
					if (pct != null) {
						VideoDetailActivity.this.prepareDownloadActionItem(
								mainMenu.findItem(R.id.menu_download), pct);
					}
				}
			} else if (ACTION_OFFLINE_VIDEO_SET_CHANGED.equals(intent.getAction())) {
				prepareDownloadActionItem(mainMenu.findItem(R.id.menu_download), PARAM_PROGRESS_UNKNOWN);
			} else if (ACTION_TOAST.equals(intent.getAction())) {
				Toast.makeText(VideoDetailActivity.this, intent.getStringExtra(EXTRA_MESSAGE), Toast.LENGTH_SHORT).show();
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		setContentView(R.layout.activity_video_detail);
		
		Intent intent = getIntent();
		videoId = 
				savedInstanceState != null && savedInstanceState.containsKey(PARAM_VIDEO_ID)
				? savedInstanceState.getString(PARAM_VIDEO_ID)
				: intent != null && intent.hasExtra(PARAM_VIDEO_ID)
				? intent.getStringExtra(PARAM_VIDEO_ID)
				: DEFAULT_VIDEO_ID;
				
		topicId = 
				savedInstanceState != null && savedInstanceState.containsKey(PARAM_TOPIC_ID)
				? savedInstanceState.getString(PARAM_TOPIC_ID)
				: intent != null && intent.hasExtra(PARAM_TOPIC_ID)
				? intent.getStringExtra(PARAM_TOPIC_ID)
				: null;
				
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(final KADataService dataService) {
				
				VideoDetailActivity.this.dataService = dataService;
				dataService.getAPIAdapter().registerUserUpdateListener(userUpdateListener);
				
				setCurrentVideo(videoId, false);
				
				if (shareActionProvider != null) {
					shareActionProvider.setShareIntent(prepareShareIntent(video));
				}
			
				User user = getCurrentUser();
				setUserVideo(user, video);
				
				setupUIForCurrentVideo();
				restoreVideoProgress();
			}
		});
	}
		
	@Override
	protected void onStart() {
		super.onStart();
		
		if (dataService != null) {
			setupUIForCurrentVideo();
		}
		
		View rightPane = findViewById(R.id.detail_right_container);
		isBigScreen = rightPane != null;
		
		if (mainMenu != null) {
			// If mainMenu is null, this activity is being created and this will happen in onCreateOptionsMenu instead.
			MenuItem dlItem = mainMenu.findItem(R.id.menu_download);
			prepareDownloadActionItem(dlItem, PARAM_PROGRESS_UNKNOWN);
		}
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_BADGE_EARNED);
		filter.addAction(ACTION_OFFLINE_VIDEO_SET_CHANGED);
		filter.addAction(ACTION_DOWNLOAD_PROGRESS_UPDATE);
		filter.addAction(ACTION_TOAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		findViewById(R.id.video_fragment_container).setOnTouchListener(videoTouchListener);
		if (dataService != null) {
			restoreVideoProgress();
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
//		super.onSaveInstanceState(outState);
		
		// Video id, position, and whether it was playing is enough to restore state.
		if (video != null) {
			// Use actual video id here, not readable_id. We want this specific video back so it has the correct parent.
			outState.putString(PARAM_VIDEO_ID, video.getId());
			outState.putString(PARAM_TOPIC_ID, topicId);
		}
		if (videoFragment != null) {
			outState.putInt(PARAM_VIDEO_POSITION, videoFragment.getVideoPosition());
			outState.putBoolean(PARAM_VIDEO_PLAY_STATE, videoFragment.isPlaying());
		}
	}
	
	@Override
	protected void onPause() {
		
		saveVideoProgress();
		
		isVideoPlayerPrepared = false;
		if (videoFragment != null) {
			videoFragment.dispose();
			videoFragment = null;
		}
		
		View container = findViewById(R.id.video_fragment_container);
		if (container != null) {
			container.setOnTouchListener(null);
		}
		
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		View video = findViewById(R.id.video_fragment_container);
		if (video != null) {
			video.setOnTouchListener(null);
		}
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (dataService != null) {
			dataService.getAPIAdapter().unregisterUserUpdateListener(userUpdateListener);
			dataService = null;
		}
		if (shareActionProvider != null) {
			shareActionProvider.setOnShareTargetSelectedListener(null);
			shareActionProvider = null;
		}
	}
		
	@Override
	public void onBackPressed() {
		if (isFullscreen() && isBigScreen) {
			if (isPortrait()) {
				goPortrait();
			} else {
				goLandscape();
			}
		} else {
			super.onBackPressed();
		}
	}
	
	private View.OnTouchListener videoTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent e) {
			if (getNavVisibility() && e.getAction() == MotionEvent.ACTION_UP && downTime != e.getDownTime()) {
				toggleNavVisibility(); // this also removes navHider callbacks.
			} else if (!getNavVisibility() && e.getAction() == MotionEvent.ACTION_DOWN) {
				downTime = e.getDownTime();
				toggleNavVisibility(); // this also removes navHider callbacks.
				if (videoFragment != null && videoFragment.isPlaying()) {
					handler.postDelayed(navHider, NAV_HIDE_DELAY);
				}
			}
			return false;
		}
	};
	
	private KAAPIAdapter.UserUpdateListener userUpdateListener = new KAAPIAdapter.UserUpdateListener() {
		@Override
		public void onUserUpdate(User user) {
			Log.d(LOG_TAG, "onUserUpdate");
			boolean loggedIn = user != null;
			
			// Look up or refresh the UserVideo.
			if (loggedIn && dataService != null) {
				try {
					Dao<UserVideo, Integer> userVideoDao = dataService.getHelper().getUserVideoDao();
					if (userVideo == null || userVideo.getUser() == null) {
						Map<String, Object> values = new HashMap<String, Object>();
						values.put("user_id", user.getNickname());
						values.put("video_id", video.getReadable_id());
						List<UserVideo> results = userVideoDao.queryForFieldValues(values);
						if (results.size() > 0) {
							Log.d(LOG_TAG, String.format("found %d results. setting first", results.size()));
							userVideo = results.get(0);
						}
					} else {
						userVideoDao.refresh(userVideo);
					}
					
					// Show the points badge.
					setPoints(userVideo.getPoints());
				
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else {
				Log.d(LOG_TAG, String.format("user: %s, dataService: %s", loggedIn ? user.getNickname() : "null", dataService));
				// User just logged out (or we strangely lost db connectivity since the last update).
				userVideo = null;
			}
			
			
		}
	};
	
	private void setUserVideo(User user, Video video) {
		Log.d(LOG_TAG, String.format("setUserVideo: %s, %s",
				user == null ? "null" : user.getNickname(),
				video == null ? "null" : video.getReadable_id()));
		
		if (user != null && video != null) {
			Dao<UserVideo, Integer> userVideoDao = null;
			try {
				userVideoDao = getDataService().getHelper().getUserVideoDao();
				QueryBuilder<UserVideo, Integer> q = userVideoDao.queryBuilder();
				q.orderBy("points", false); // In case any duplicates have slipped in, use highest point total.
				q.where().eq("user_id", user.getNickname())
				 .and().eq("video_id", video.getReadable_id());
				PreparedQuery<UserVideo> pq = q.prepare();
				userVideo = userVideoDao.queryForFirst(pq);
				if (userVideo == null) {
					// This is possible if the user is watching this video for the first time.
					userVideo = new UserVideo();
					userVideo.setUser(user);
					userVideo.setVideo_id(video.getReadable_id());
					// Better save here, as we need the UserVideo to have an id before we post a progress update.
					userVideoDao.create(userVideo);
				} else {
					Log.d(LOG_TAG, "userVideo exists (" + userVideo.getPoints() + ") last watched: " + userVideo.getLast_watched());
				}
			} catch (SQLException e) {
				// Fail silently if we can't find the UserVideo.
				e.printStackTrace();
			} catch (ServiceUnavailableException e) {
				e.printStackTrace();
			}
			
			// Show the points badge.
			setPoints(userVideo.getPoints());
		} else if (video != null) {
			userVideo = new UserVideo();
			userVideo.setVideo_id(video.getReadable_id());
		}
	}
		
	private void setCurrentVideo(String readableId, boolean replace) {
		try {
			Dao<Video, String> videoDao = getDataService().getHelper().getVideoDao();
			QueryBuilder<Video, String> q = videoDao.queryBuilder();
			q.where().eq("readable_id", readableId);
			video = videoDao.queryForFirst(q.prepare());
			if (topicId == null) {
				// This *should* never be the case.
				topicId = video.getParentTopic().getId();
			}
			
			// Grab next video id.
			Log.d(LOG_TAG, "looking up next video");
			RawRowMapper<Video> mapper = videoDao.getRawRowMapper();
			GenericRawResults<Video> results = videoDao.queryRaw(
					"select video.* from video,topicvideo where topicvideo.topic_id=? and topicvideo.video_id=video.readable_id and video.seq>? order by video.seq limit 1",
					mapper, new String[] {topicId, "" + video.getSeq()});
			final Video v = results.getFirstResult();
			if (v != null) {
				nextVideoId = v.getId();
				Log.d(LOG_TAG, "  -> " + nextVideoId);
				if (mainMenu != null) {
					mainMenu.findItem(R.id.menu_next).setEnabled(true).setVisible(true);
				}
			} else {
				Log.d(LOG_TAG, "  -> oops");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ServiceUnavailableException e) {
			e.printStackTrace();
		}
		
		rightNow.setToNow();
		lastPost = (int) (rightNow.toMillis(true) / 1000);
		percentLastSaved = 0;
		saving = false;
	}
	
	private void setupUIForCurrentVideo() {
		if (videoFragment != null) {
			videoFragment.dispose();
		}
		
		Bundle args = new Bundle();
		args.putString(Constants.PARAM_VIDEO_ID, videoId);
		
		videoFragment = new VideoFragment();
		videoFragment.registerCallbacks(this);
		videoFragment.setArguments(args);
		
		FragmentTransaction tx = getFragmentManager().beginTransaction()
			.replace(R.id.video_fragment_container, videoFragment, TAG_VIDEO_FRAGMENT);
		
		currentOrientation = getResources().getConfiguration().orientation;
		onOrientationChanged(currentOrientation);
		
		if (isPortrait()) {
			captionFragment = new CaptionFragment();
			captionFragment.registerCallbacks(this);
			if (userVideo != null && userVideo.getUser() != null) {
				args.putInt(PARAM_USERVIDEO_POINTS, userVideo.getPoints());
			}
			captionFragment.setArguments(args);
			
			tx.replace(R.id.detail_bottom_container, captionFragment, TAG_CAPTION_FRAGMENT);
		}
		
		tx.commit();
	}
	
	@Override
	public void onVideoPrepared() {
		Log.d(LOG_TAG, "onVideoPrepared");
		isVideoPlayerPrepared = true;
		shouldShowVideoControls = true;
		setControlsVisible(getNavVisibility());
		videoFragment.seekTo(desiredSeekPosition);
	}
	
	@Override
	public void onVideoStarted() {
		Log.d(LOG_TAG, "onVideoStarted");
		rightNow.setToNow();
		lastPost = (int) (rightNow.toMillis(true) / 1000);
		saveVideoProgress();
		handler.removeCallbacks(navHider);
		handler.postDelayed(navHider, NAV_HIDE_DELAY);
	}

	@Override
	public void onVideoStopped() {
		Log.d(LOG_TAG, "onVideoStopped");
		setNavVisibility(true);
		saveVideoProgress();
	}
	
	@Override
	public void onVideoCompleted() {
		Log.d(LOG_TAG, "onVideoCompleted");
		isVideoPlayerPrepared = false;
	}

	@Override
	public void onPositionUpdate(int ms) {
		
		if (captionFragment != null) {
			captionFragment.onPositionUpdate(ms);
		}
		
		if (getCurrentUserId() == null) {
			return;
		}
		
		rightNow.setToNow();
		int secondsNow = (int) (rightNow.toMillis(true) / 1000);
		boolean enoughTimeHasPassed = secondsNow - lastPost > Constants.LOG_INTERVAL_SECONDS;
		
		float percent = videoFragment.getPercentWatched();
		boolean enoughVideoHasPlayed = percent > percentLastSaved + Constants.LOG_INTERVAL_PERCENT;
		
		if (enoughTimeHasPassed && enoughVideoHasPlayed) {
			// TODO : respect me
			boolean offline = false;
			if (!offline) {
				postVideoProgress();
			}
		}
		
	}
	
	@Override
	public void onFullscreenToggleRequested() {
		if (isFullscreen()) {
			if (isPortrait()) {
				goPortrait();
			} else {
				goLandscape();
			}
		} else {
			goFullscreen();
		}
	}
	
	/**
	 * Seek video to last watched position.
	 */
	private void restoreVideoProgress() {
		Log.d(LOG_TAG, "restoreVideoProgress");
		desiredSeekPosition = 0;
		if (userVideo != null) {
			int sec = userVideo.getLast_second_watched();
			if (video.getDuration() - sec > 1) {
				desiredSeekPosition = sec * 1000;
				if (isVideoPlayerPrepared) {
					videoFragment.seekTo(desiredSeekPosition);
				}
			}
		}
	}
	
	/**
	 * Update userVideo with latest data and save it.
	 */
	private void saveVideoProgress() {
		Log.d(LOG_TAG, "saveVideoProgress");
		
		if (videoFragment != null && userVideo != null && userVideo.getUser() != null) {
			int secondsWatched = videoFragment.getClampedSecondsWatchedSince(lastPost);
			int lastSecondWatched = videoFragment.getSecondsWatched();
			Log.d(LOG_TAG, String.format("last: %d, total: %d", lastSecondWatched, secondsWatched));
			
			desiredSeekPosition = 1000 * lastSecondWatched;
			
			// Update the UserVideo object and save it to the db.
			userVideo.setLast_second_watched(lastSecondWatched);
			userVideo.setSeconds_watched(secondsWatched);
		
			try {
				dataService.getHelper().getUserVideoDao().update(userVideo);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Post relevant userVideo data to khan servers.
	 */
	private void postVideoProgress() {
		if (!saving) {
			Log.d(LOG_TAG, "postVideoProgress");
			saving = true;
			final float percent = videoFragment.getPercentWatched();
			Log.d(LOG_TAG, String.format("%%: %f", percent));
			
			Runnable success = new Runnable() {
				public void run() {
					rightNow.setToNow();
					lastPost = (int) (rightNow.toMillis(true) / 1000);
					finishVideoProgressSave(percent);
				}
			};
			
			Runnable error = new Runnable() {
				public void run() {
					finishVideoProgressSave(percentLastSaved);
				}
			};
			
			// Save progress to ensure correct values are on the userVideo before posting.
			saveVideoProgress();
			
			// The api adapter causes a user update after posting this.
			dataService.getAPIAdapter().postVideoProgress(userVideo, success, error);
		}
	}
	
	private void finishVideoProgressSave(float percent) {
		percentLastSaved = percent;
		saving = false;
	}

	@Override
	public void onPositionRequested(int ms) {
		if (videoFragment != null) {
			videoFragment.seekTo(ms);
		}
	}
	
	@Override
	public void onCaptionsUnavailable() {
		Log.d(LOG_TAG, "onCaptionsUnavailable");
		if (isBigScreen) {
			findViewById(R.id.detail_right_header).setVisibility(View.GONE);
		}
	}
	
	@Override
	public void onCaptionsLoaded() {
		if (isBigScreen) {
			findViewById(R.id.detail_right_header).setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public void onDownloadRequested(Video video) {
		OfflineVideoManager ovm;
		try {
			ovm = getDataService().getOfflineVideoManager();
			ovm.downloadVideo(video);
		} catch (ServiceUnavailableException e) {
			e.printStackTrace();
		}
	}

	private String getCurrentUserId() {
		return getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE)
				.getString(Constants.SETTING_USERID, null);
	}
	
	/**
	 * Get the current user.
	 * 
	 * @return the user or null.
	 */
	private User getCurrentUser() {
		Log.v(LOG_TAG, "getCurrentUser");
		String userid = getCurrentUserId();
		if (userid == null) { // no user saved
			Log.v(LOG_TAG, "  --> null (no id)");
			return null;
		}
		
		User user = null;
		try {
			user = getDataService().getHelper().getUserDao().queryForId(userid);
		} catch (SQLException e) {
			// That's fine; pretend no user was logged in.
			e.printStackTrace();
		} catch (ServiceUnavailableException e) {
			// That's fine; pretend no user was logged in.
			e.printStackTrace();
		}
		Log.v(LOG_TAG, String.format(" --> %s", user == null ? "null" : user.getNickname()));
		return user;
	}
	
	private boolean isPortrait() {
		return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
	}
	
	private boolean isFullscreen() {
		return isFullscreen;
	}
	
	private void launchVideoDetailActivity(String videoId, String topicId) {
		// TODO : animate the transition
		Intent intent = new Intent(this, VideoDetailActivity.class);
		intent.putExtra(PARAM_VIDEO_ID, videoId);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		startActivity(intent);
	}
	
	private void launchListActivity(String topicId, Class<?> activityClass) {
		Intent intent = new Intent(this, activityClass);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		// ALWAYS goes to this video's parent topic. If this assumption breaks, then we must rethink the clear_top flag.
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}
	
	private Intent prepareShareIntent(Video video) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		
		String url = video.getKa_url();
		intent.putExtra("title", video.getTitle());
		intent.putExtra("url", url);
		intent.putExtra("desc", video.getDescription());
		
		intent.setType("text/plain");
		
		return intent;
	}
	
	private void prepareDownloadActionItem(MenuItem item, int downloadPercent) {
		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		int dlRes = R.drawable.av_download;
		int removeRes = R.drawable.content_discard;
		
		switch (downloadPercent) {
		case PARAM_PROGRESS_DONE:
			videoIsDownloaded = true;
			item.setEnabled(true).setTitle("Downloaded").setIcon(getResources().getDrawable(removeRes));
			break;
		case PARAM_PROGRESS_UNKNOWN:
			if (video != null) {
				try {
					dataService.getHelper().getVideoDao().refresh(video);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				switch (video.getDownload_status()) {
				case Video.DL_STATUS_COMPLETE:
					videoIsDownloaded = true;
					item.setEnabled(true).setTitle("Downloaded").setIcon(getResources().getDrawable(removeRes));
					break;
				case Video.DL_STATUS_IN_PROGRESS:
					videoIsDownloaded = false;
					item.setEnabled(false).setTitle("Downloading").setIcon(getResources().getDrawable(dlRes));
					break;
				case Video.DL_STATUS_NOT_STARTED:
				default:
					videoIsDownloaded = false;
					item.setEnabled(true).setTitle("Download").setIcon(getResources().getDrawable(dlRes));
				}
			}
			break;
		default:
			videoIsDownloaded = false;
			item.setEnabled(false).setTitle(downloadPercent + "%").setIcon(null);
		}
	}
	
	private void prepareShareActionItem(MenuItem shareItem) {
		shareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
		shareActionProvider.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
		shareActionProvider.setOnShareTargetSelectedListener(shareTargetSelectedListener);
		shareActionProvider.setShareIntent(prepareShareIntent(video));
	}
	
	ShareActionProvider.OnShareTargetSelectedListener shareTargetSelectedListener = new ShareActionProvider.OnShareTargetSelectedListener() {
		@Override
		public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
			Log.d(LOG_TAG, "onShareTargetSelected: " + intent.getStringExtra(Intent.EXTRA_TEXT));
			
			String lowerFqn = intent.getComponent().getClassName().toLowerCase(Locale.US);
			
			// Twitter
			if (lowerFqn.contains("twitter") || lowerFqn.contains("tweet")) {
				// Twitter clients use the EXTRA_TEXT only, with 140c limit.
				int tweetLength = 140;
				String tweetFormat = "just learned about \"%s\" (%s) via @khanacademy";
				// As an example, the following tweet is 127 characters.
				// just learned about 'Lebron Asks: What are the chances of making 10 free throws in a row?' (20 char link is here) via @khanacademy
				
				// Links in tweets take exactly 20 characters (https://support.twitter.com/articles/78124-how-to-post-links-urls-in-tweets).
				int linkLength = 20;
				
				
				int usedLength = tweetFormat.length() - "%s".length() + linkLength; // We know the length the link will take.
				usedLength -= "%s".length(); // The title will replace an instance of this string.
				
				// Truncate the title to fit the tweet.
				int remainingSpace = tweetLength - usedLength;
				String title = intent.getStringExtra("title");
				if (title.length() > remainingSpace) {
					title = title.substring(0, remainingSpace - 3) + "...";
				}
				
				String tweet = String.format(tweetFormat, title, intent.getStringExtra("url"));
				intent.putExtra(Intent.EXTRA_TEXT, tweet);
			}
			
			else {
				String subject = "just learned about " + intent.getStringExtra("title");
				intent.putExtra(Intent.EXTRA_SUBJECT, subject);
				
				String bodyFormat = "\"%s\" (%s) is one of nearly 4,000 great educational videos at http://www.khanacademy.org/.";
				String body = String.format(bodyFormat, intent.getStringExtra("title"), intent.getStringExtra("url"));
				
				String desc = intent.getStringExtra("desc");
				if (desc != null && desc.length() > 0) {
					body += "\n\nVideo description: " + desc;
				}
				intent.putExtra(Intent.EXTRA_TEXT, body);
			}
			
			// In docs for this method: "NOTE: Modifying the intent is not permitted and any changes to the latter will be ignored."
			// Since we need to modify the intent (that's the whole point of this callback, isn't it?!), we launch the activity ourselves.
			// Launch in new task, hopefully avoiding the case where the user already has facebook open to another activity and we don't see the share activity.
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		}
	};
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mainMenuDelegate = new MainMenuDelegate(this);
		mainMenu = menu;
		
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.video_detail, menu);
		
		MenuItem dlItem = menu.findItem(R.id.menu_download);
		prepareDownloadActionItem(dlItem, PARAM_PROGRESS_UNKNOWN);
		
		MenuItem shareItem = menu.findItem(R.id.menu_share);
		prepareShareActionItem(shareItem);
		
		if (nextVideoId == null) {
			mainMenu.findItem(R.id.menu_next).setVisible(false).setEnabled(false);
		}
		
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
	
	private void promptAndDeleteDownloadedVideo(final Video video) {
	    new AlertDialog.Builder(this)
        .setMessage(getString(R.string.msg_delete_video))
        .setPositiveButton(getString(R.string.button_confirm_delete), new DialogInterface.OnClickListener() {
        	@Override
            public void onClick(DialogInterface dialog, int id) {
        		deleteDownloadedVideo(video);
				VideoDetailActivity.this.prepareDownloadActionItem(
						mainMenu.findItem(R.id.menu_download), PARAM_PROGRESS_UNKNOWN);
            }
        })
        .setNegativeButton(getString(R.string.button_cancel), null)
        .show();
		
	}
	private void deleteDownloadedVideo(Video video) {
		final Set<Video> toDelete = new HashSet<Video>(1);
		toDelete.add(video);
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				dataService.getOfflineVideoManager().deleteOfflineVideos(toDelete);
				Toast.makeText(VideoDetailActivity.this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		android.util.Log.w(LOG_TAG, "onOptionsItemSelected");
		Log.d(LOG_TAG, "onOptionsItemSelected");
		if (mainMenuDelegate.onOptionsItemSelected(item)) {
			return true;
		}
	    switch (item.getItemId()) {
	    case R.id.menu_next:
	    	if (nextVideoId != null && topicId != null) {
	    		launchVideoDetailActivity(nextVideoId, topicId);
	    	}
	    	return true;
	    case R.id.menu_logout:
	    	dataService.getAPIAdapter().logout();
	    	return true;
        case R.id.menu_download:
        	if (!videoIsDownloaded) {
	        	onDownloadRequested(video);
        	} else {
        		promptAndDeleteDownloadedVideo(video);
        	}
        	return true;
		case android.R.id.home:
			// VideoList for this video's parent topic.
			if (isFullscreen() && isBigScreen) {
				if (isPortrait()) {
					goPortrait();
				} else {
					goLandscape();
				}
			} else {
				launchListActivity(topicId, VideoListActivity.class);
			}
			return true;
		default:
	        return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		if (config.orientation != currentOrientation) onOrientationChanged(config.orientation);
	}
	
	private void onOrientationChanged(final int orientation) {
		currentOrientation = orientation;
		
		if (!isFullscreen || !isBigScreen) {
			switch (orientation) {
			case Configuration.ORIENTATION_LANDSCAPE:
				goLandscape();
				break;
			case Configuration.ORIENTATION_PORTRAIT:
				goPortrait();
				break;
			default:
			}
		}
	}
	
	private void createAndAttachCaptionFragment(int containerId) {
		FragmentTransaction tx = getFragmentManager().beginTransaction();
		
		if (captionFragment != null) {
			tx.remove(captionFragment);
		}
		
		captionFragment = new CaptionFragment();
		Bundle args = new Bundle();
		if (video != null) {
			args.putString(Constants.PARAM_VIDEO_ID, video.getId());
			if (videoFragment != null) {
				args.putInt(PARAM_VIDEO_POSITION, videoFragment.getVideoPosition());
			}
		}
		// Set args even if empty to avoid possible NPE inside CaptionFragment.
		captionFragment.setArguments(args);
		captionFragment.registerCallbacks(this);
		
		tx.replace(containerId, captionFragment);
		tx.commit();
		// Force execute, so we can populateHeader afterward.
		getFragmentManager().executePendingTransactions();
	}
	
	private void createAndAttachHeader() {
		FrameLayout container = (FrameLayout) findViewById(R.id.detail_header_container);
		if (headerView == null) {
			headerView = getLayoutInflater().inflate(R.layout.video_header, container, true);
			populateHeader();
			pointsView = (TextView) findViewById(R.id.video_points);
			if (userVideo != null && userVideo.getUser() != null) {
				setPoints(userVideo.getPoints());
			}
		}
	}
	private void populateHeader() {
		View headerView = findViewById(R.id.video_header);
		Log.d(LOG_TAG, "populateHeader: header is " + (headerView == null ? "null" : "not null"));
		if (video != null && headerView != null) {
			((TextView) headerView.findViewById(R.id.video_title)).setText(video.getTitle());
			String desc = video.getDescription();
			TextView descView = (TextView) headerView.findViewById(R.id.video_description);
			if (desc != null && desc.length() > 0) {
				descView.setText(desc);
				descView.setVisibility(View.VISIBLE);
				descView.setMovementMethod(new ScrollingMovementMethod());
			} else {
				descView.setVisibility(View.GONE);
			}
		}
	}
	
	private void goFullscreen() {
		goFullscreen(true);
	}
	
	private void goFullscreen(boolean force) {
		isFullscreen = true;
		setRequestedOrientation(force ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
				: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		
		VideoController videoControls = (VideoController) findViewById(R.id.controller);
		if (videoControls != null) {
			videoControls.setFullscreen(true);
		}
		
		ThumbnailWrapper videoContainer = (ThumbnailWrapper) findViewById(R.id.video_fragment_container);
		videoContainer.setMaintainAspectRatio(false);
		
		if (captionFragment != null) {
			FragmentTransaction tx = getFragmentManager().beginTransaction();
			tx.remove(captionFragment);
			tx.commit();
		}
		findViewById(R.id.detail_bottom_container).setVisibility(View.GONE);
		if (isBigScreen) {
			findViewById(R.id.detail_right_container).setVisibility(View.GONE);
			findViewById(R.id.detail_center_divider).setVisibility(View.GONE);
		}
		
		setNavVisibility(videoFragment == null || !videoFragment.isPlaying());
		
		getDecorViewTreeObserver().addOnGlobalLayoutListener(layoutFixer);
	}
	
	private void goLandscape() {
		if (isBigScreen) {
			goLargeLandscape();
		} else {
			goFullscreen(false);
		}
	}
	
	private void goLargeLandscape() {
		isFullscreen = false;
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		
		VideoController videoControls = (VideoController) findViewById(R.id.controller);
		if (videoControls != null) {
			videoControls.setFullscreen(false);
		}
		
		ThumbnailWrapper videoContainer = (ThumbnailWrapper) findViewById(R.id.video_fragment_container);
		videoContainer.setMaintainAspectRatio(true);
		
		FrameLayout headerContainer = (FrameLayout) findViewById(R.id.detail_header_container);
		LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) headerContainer.getLayoutParams();
		p.weight = 1;
		headerContainer.setLayoutParams(p);
		headerContainer.setVisibility(View.VISIBLE);
		
		FrameLayout emptyContainer = (FrameLayout) findViewById(R.id.detail_bottom_container);
		p = (LinearLayout.LayoutParams) emptyContainer.getLayoutParams();
		p.weight = 0;
		emptyContainer.setLayoutParams(p);
		emptyContainer.setVisibility(View.GONE);
		
		findViewById(R.id.detail_right_container).setVisibility(View.VISIBLE);
		createAndAttachCaptionFragment(R.id.detail_right_caption_container);
		createAndAttachHeader();
		
		findViewById(R.id.detail_center_divider).setVisibility(View.VISIBLE);
		
		getDecorViewTreeObserver().addOnGlobalLayoutListener(layoutFixer);
	}
	
	private void goPortrait() {
		isFullscreen = false;
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		
		VideoController videoControls = (VideoController) findViewById(R.id.controller);
		if (videoControls != null) {
			videoControls.setFullscreen(false);
		}
		
		ThumbnailWrapper videoContainer = (ThumbnailWrapper) findViewById(R.id.video_fragment_container);
		videoContainer.setMaintainAspectRatio(true);
		
		FrameLayout headerContainer = (FrameLayout) findViewById(R.id.detail_header_container);
		LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) headerContainer.getLayoutParams();
		p.weight = 0;
		headerContainer.setLayoutParams(p);
		
		createAndAttachCaptionFragment(R.id.detail_bottom_container);
		FrameLayout captionContainer = (FrameLayout) findViewById(R.id.detail_bottom_container);
		captionContainer.setVisibility(View.VISIBLE);
		p = (LinearLayout.LayoutParams) captionContainer.getLayoutParams();
		p.weight = 1;
		captionContainer.setLayoutParams(p);
		
		if (isBigScreen) {
			findViewById(R.id.detail_right_container).setVisibility(View.GONE);
			findViewById(R.id.detail_center_divider).setVisibility(View.GONE);
		}
		
		setNavVisibility(true);
		createAndAttachHeader();
		
		getDecorViewTreeObserver().addOnGlobalLayoutListener(layoutFixer);
	}
	
	public void setPoints(int points) {
		Log.d(LOG_TAG, "setPoints (" + points + ")");
		if (pointsView == null) {
			return;
		}
		if (points == POINTS_GONE) {
			pointsView.setVisibility(View.GONE);
		} else {
			pointsView.setText(String.format("%d", points));
			pointsView.setVisibility(View.VISIBLE);
		}
	}
	
	private ViewTreeObserver getDecorViewTreeObserver() {
		ViewTreeObserver decorViewTreeObserver = getWindow().getDecorView().getViewTreeObserver();
		return decorViewTreeObserver;
	}
	
	int previousActionBarHeight = -1;
	// Runs after orientation changes to push the portrait views down below the overlaid actionbar. Running this code
	// directly in onOrientationChanged caused it to fail the first time we entered portrait orientation.
	private ViewTreeObserver.OnGlobalLayoutListener layoutFixer = new ViewTreeObserver.OnGlobalLayoutListener() {
		
		@Override
		public void onGlobalLayout() {
			if (isFullscreen()) {
				final View containerView = findViewById(R.id.detail_pane_container);
				final FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) containerView.getLayoutParams();
				p.setMargins(0, 0, 0, 0);
				containerView.setLayoutParams(p);
				previousActionBarHeight = 0;
			} else {
				getActionBar().show();
				int actionBarHeight = getActionBar().getHeight();
				
				// Action bar height changes between landscape and portrait on Fire HD (!)
				if (actionBarHeight != previousActionBarHeight) {
					// First one fails, second one works. (4.2.1)
		//			p.topMargin = getActionBar().getHeight();
		//			p.setMargins(0, getActionBar().getHeight(), 0, 0);
			
					final View containerView = findViewById(R.id.detail_pane_container);
					final FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) containerView.getLayoutParams();
					p.setMargins(0, actionBarHeight, 0, 0);
					containerView.setLayoutParams(p);
					previousActionBarHeight = actionBarHeight;
				}
			}
			
			getDecorViewTreeObserver().removeGlobalOnLayoutListener(this);
		}
	};
	
	private void toggleNavVisibility() {
		setNavVisibility(!getNavVisibility());
	}
	private boolean getNavVisibility() {
		return navVis;
	}
	private void setNavVisibility(boolean visible) {
		Log.d(LOG_TAG, "setNavVisibility: " + visible);
		// Fire HD:
		// View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN : These do nothing.
		// View.SYSTEM_UI_FLAG_HIDE_NAVIGATION : This works as expected, but isn't good enough.
		// WindowManager.LayoutParams.FLAG_FULLSCREEN : This is great, but 
				// a) can't keep stable layout (video grows when added, shrinks when removed),
				// b) No way to hook the event where the user touches the handle, so that behavior differs from a touch on the rest of the screen.
		
		handler.removeCallbacks(navHider);
		navVis = visible;
		if (visible) {
			// Flags, menu items, and action bar should already be correct in portrait, but it won't hurt.
			findViewById(R.id.detail_pane_container).setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
			getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		    getActionBar().show();
		    if (mainMenu != null) {
			    for (int i=0; i<mainMenu.size(); ++i) {
			    	mainMenu.getItem(i).setVisible(true);
			    }
		    }
		    
		    setControlsVisible(shouldShowVideoControls);
			
			if (videoFragment != null && videoFragment.isPlaying()) {
				handler.postDelayed(navHider, NAV_HIDE_DELAY);
			}
		} else {
			if (isFullscreen()) {
				findViewById(R.id.detail_pane_container).setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			    getActionBar().hide();
			    // Hide menu items in landscape to prevent the action overflow menu / share menu from
			    // disappearing on nav hide when the user is trying to interact with them.
			    // The right solution would be to force the nav to stay open while the menus are open, 
			    // but there doesn't appear to be a way to check (?!). We get NO call to onOptionsMenuClosed
			    // when that is overridden, so we cannot track it ourselves either. In fact, if the options menu
			    // is open it consumes the first touch event outside itself to close itself, so we can't even
			    // do it by listening to touches and assuming they close the menu.
			    if (mainMenu != null) {
				    for (int i=0; i<mainMenu.size(); ++i) {
				    	mainMenu.getItem(i).setVisible(false);
				    }
			    }
			}
			setControlsVisible(false);
		}
    }
	
	private void setControlsVisible(boolean visible) {
		VideoController videoControls = (VideoController) findViewById(R.id.controller);
		if (videoControls != null) {
			if (visible) {
				videoControls.show();
			} else {
				videoControls.hide();
			}
		}
	}
	
	private Runnable navHider = new Runnable() {
		@Override
		public void run() {
			if (!isFinishing()) {
				setNavVisibility(false);
			}
		}
	};
	
}
