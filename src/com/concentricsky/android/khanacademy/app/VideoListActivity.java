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
import static com.concentricsky.android.khanacademy.Constants.ACTION_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.ACTION_OFFLINE_VIDEO_SET_CHANGED;
import static com.concentricsky.android.khanacademy.Constants.ACTION_TOAST;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_BADGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_MESSAGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_STATUS;
import static com.concentricsky.android.khanacademy.Constants.PARAM_SHOW_DL_ONLY;
import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_ID;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.MainMenuDelegate;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.Thumbnail;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.data.remote.KAAPIAdapter;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.util.ThumbnailManager;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer.Param;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.Where;

public class VideoListActivity extends KADataServiceProviderActivityBase {
	
	public static final String LOG_TAG = VideoListActivity.class.getSimpleName();
	private static final int DOWNLOAD_ITEM_ID = 1543413;
	
	private String topicId;
	private Topic topic;
	private View headerView;
	private AbsListView listView;
	private ThumbnailManager thumbnailManager;
	private KAAPIAdapter api;
	private boolean isShowingDownloadedVideosOnly;
	private String[] displayOptions = new String[] { "All Videos", "Downloaded Videos" };
	private SpinnerAdapter displayOptionsAdapter;
	private MainMenuDelegate mainMenuDelegate;
	private Menu mainMenu;
	private KADataService dataService;
	private Cursor topicCursor;
	private ExecutorService thumbExecutor;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_LIBRARY_UPDATE.equals(intent.getAction()) && topic != null) {
				Log.d(LOG_TAG, "library update broadcast received");
				setParentTopic(topic);
			} else if (ACTION_BADGE_EARNED.equals(intent.getAction()) && dataService != null) {
				Badge badge = (Badge) intent.getSerializableExtra(EXTRA_BADGE);
				dataService.getAPIAdapter().toastBadge(badge);
			} else if (ACTION_DOWNLOAD_PROGRESS_UPDATE.equals(intent.getAction()) && listView != null) {
				@SuppressWarnings("unchecked")
				Map<String, Integer> status = (Map<String, Integer>) intent.getSerializableExtra(EXTRA_STATUS);
				VideoAdapter adapter = (VideoAdapter) listView.getAdapter();
				adapter.setStatus(status);
				adapter.updateBars();
			} else if (ACTION_OFFLINE_VIDEO_SET_CHANGED.equals(intent.getAction()) && listView != null) {
				resetListContents(topicId);
			} else if (ACTION_TOAST.equals(intent.getAction())) {
				Toast.makeText(VideoListActivity.this, intent.getStringExtra(EXTRA_MESSAGE), Toast.LENGTH_SHORT).show();
			}
		}
		
	};
	
	// Used to avoid touching the ui with AsyncTask callbacks after the ui is no longer available.
	boolean stopped = false;
	
	private ActionBar.OnNavigationListener navListener = new ActionBar.OnNavigationListener() {
		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			Log.d(LOG_TAG, "onNavigationItemSelected: " + itemPosition);
			
			isShowingDownloadedVideosOnly = itemPosition == 1;
			if (topic != null) {
				setParentTopic(topic);
			}

			return true;
		}
	};
	private KAAPIAdapter.UserUpdateListener userUpdateListener = new KAAPIAdapter.UserUpdateListener() {
		@Override
		public void onUserUpdate(User user) {
			resetListContents(topicId);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_video_list);
		
		Intent intent = getIntent();
		topicId = savedInstanceState != null && savedInstanceState.containsKey(PARAM_TOPIC_ID)
				? savedInstanceState.getString(PARAM_TOPIC_ID)
				: intent != null && intent.hasExtra(PARAM_TOPIC_ID)
				? intent.getStringExtra(PARAM_TOPIC_ID)
				: null;
		
		isShowingDownloadedVideosOnly = 
				savedInstanceState != null && savedInstanceState.containsKey(PARAM_SHOW_DL_ONLY)
				? savedInstanceState.getBoolean(PARAM_SHOW_DL_ONLY)
				: intent != null && intent.hasExtra(PARAM_SHOW_DL_ONLY)
				? intent.getBooleanExtra(PARAM_SHOW_DL_ONLY, false)
				: false;
	}
	
	@Override
	protected void onStart() {
		Log.d(LOG_TAG, "onStart");
		super.onStart();
		stopped = false;
		
		mainMenuDelegate = new MainMenuDelegate(this);
		
		listView = (AbsListView) findViewById(android.R.id.list);
		listView.setOnItemClickListener(clickListener);

		if (listView instanceof ListView) {
			// It is important that this is inflated with listView passed as the parent, despite the attach false parameter.
			// Otherwise, the view ends up with incorrect LayoutParams and we see crazy, crazy behavior.
			headerView = getLayoutInflater().inflate(R.layout.header_video_list, listView, false);
			ListView lv = (ListView) listView;
			if (lv.getHeaderViewsCount() == 0) {
				lv.addHeaderView(headerView);
			}
		} else { // GridView, fixed header
			headerView = findViewById(R.id.header_video_list);
		}
		
		
		/**  Responsive layout stuff 
		 * 
		 *  Based on screen width, we will find either
		 *   narrow
		 *     a listview with a header view
		 *     items are a thumbnail to the left and a title on white space to the right.
		 *     header is a thumbnail with overlaid title across the bottom
		 * 
		 *   middle
		 *     a fixed header on top and a grid view below
		 *     header is thumb to left, title above scrolling description to right
		 *     items are thumbs with title overlaid across the bottom (3 across)
		 *   
		 *   wide
		 *     a fixed header to the left and a grid view on the right
		 *     header is thumb on top, title next, description at bottom
		 *     items are thumbs with title overlaid across the bottom (3 across)
		 *  
		 *  
		 *  So in this class, we 
		 *    find view by id 'list'
		 *    if it's a ListView, inflate and attach header view
		 *    if not, then the header is fixed and already in the layout
		 *    either way, now we can find header views by id
		 *    adapter is the same either way
		 *  
		 *  
		 *  
		 *  **/
		
		
		
		
		
		
		ActionBar ab = getActionBar();
		displayOptionsAdapter = new ArrayAdapter<String>(getActionBar().getThemedContext(), android.R.layout.simple_list_item_1, displayOptions);
		ab.setDisplayHomeAsUpEnabled(true);
		ab.setTitle("");
		ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		ab.setListNavigationCallbacks(displayOptionsAdapter, navListener);
		ab.setSelectedNavigationItem(isShowingDownloadedVideosOnly ? 1 : 0);
		
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(KADataService dataService) {
				VideoListActivity.this.dataService = dataService;
				
				if (topicId != null) {
					Dao<Topic, String> topicDao;
					try {
						topicDao = dataService.getHelper().getTopicDao();
						topic = topicDao.queryForId(topicId);
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				else {
					Log.e(LOG_TAG, "Topic id not set for video list");
					topic = dataService.getRootTopic();
					topicId = topic.getId();
				}
				
				thumbnailManager = dataService.getThumbnailManager();
				api = dataService.getAPIAdapter();
				api.registerUserUpdateListener(userUpdateListener);
				
				// This instead happens in ActionBar.OnNavigationListener#onNavigationItemSelected, which
				// fires after onResume.
//				setParentTopic(topic);
			}
		});
				
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_LIBRARY_UPDATE);
		filter.addAction(ACTION_BADGE_EARNED);
		filter.addAction(ACTION_OFFLINE_VIDEO_SET_CHANGED);
		filter.addAction(ACTION_DOWNLOAD_PROGRESS_UPDATE);
		filter.addAction(ACTION_TOAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
		thumbExecutor = Executors.newSingleThreadExecutor();
	}
	
	@Override
	protected void onStop() {
		Log.d(LOG_TAG, "onStop");
		stopped = true;
		
		getActionBar().setListNavigationCallbacks(null, null);
		if (listView != null) {
			// Could probably go through and cancel all ThumbLoaders here.
			VideoAdapter adapter = (VideoAdapter) listView.getAdapter();
			if (adapter != null) {
				adapter.renderer.stop();
				adapter.renderer.clearCache();
				adapter.changeCursor(null);
			}
			listView.setAdapter(null);
			listView.setOnItemClickListener(null);
			listView = null;
		}
		if (api != null) {
			api.unregisterUserUpdateListener(userUpdateListener);
		}
		mainMenuDelegate = null;
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		thumbExecutor.shutdownNow();
		super.onStop();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mainMenu = menu;
		mainMenuDelegate.onCreateOptionsMenu(menu);
		
		MenuItem downloadItem = mainMenu.add(0, DOWNLOAD_ITEM_ID, mainMenu.size(), R.string.menu_item_download_all);
		downloadItem.setIcon(R.drawable.av_download);
		downloadItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
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
		case android.R.id.home:
			// TopicList for this topic's parent topic, or home in case of "root".
			if (topic == null) {
				// Not sure what to do.
				launchHomeActivity();
			}
			else {
				Topic parentTopic = topic.getParentTopic();
				try {
					dataService.getHelper().getTopicDao().refresh(parentTopic);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				if (parentTopic == null) {
					// This is the root topic. How did that happen?
					launchHomeActivity();
				}
				else {
					if (parentTopic.getParentTopic() == null) {
						// The parent is the root topic.
						launchHomeActivity();
					}
					else {
						launchListActivity(parentTopic.getId(), TopicListActivity.class);
					}
				}
			}
			return true;
		case DOWNLOAD_ITEM_ID:
			confirmAndDownloadAll();
			return true;
		default:
			return super.onOptionsItemSelected(item);
	    }
	}
	
	private void confirmAndDownloadAll() {
		Dao<Video, String> videoDao;
		try {
			videoDao = dataService.getHelper().getVideoDao();
			// TODO : Instead, startService with a topicId and let the service background the lookup.
			//        This would take some callback juggling, though, as we want the count available for the dialog.
			final List<Video> toDownload = videoDao.queryRaw(
					"select video.* from video, topicvideo where topicvideo.video_id=video.readable_id and topicvideo.topic_id=? and video.download_status<?",
					videoDao.getRawRowMapper(), topicId, String.valueOf(Video.DL_STATUS_COMPLETE)).getResults();
			
			String msg = "";
			int size = toDownload.size();
			switch (size) {
			case 0:
			case 1:
				dataService.getOfflineVideoManager().downloadAll(toDownload);
				return;
			case 2:
				msg = getString(R.string.msg_download_both);
				break;
			default:
				msg = String.format(getString(R.string.msg_download_all), toDownload.size());
			}
			
		    new AlertDialog.Builder(VideoListActivity.this)
	        .setMessage(msg)
	        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
	        	@Override
	            public void onClick(DialogInterface dialog, int id) {
					dataService.getOfflineVideoManager().downloadAll(toDownload);
	            }
	        })
	        .setNegativeButton(android.R.string.no, null)
	        .show();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private AdapterView.OnItemClickListener clickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Cursor cursor = (Cursor) listView.getItemAtPosition(position);
			if (cursor != null) { // This happened once on Fire HD when clicking outside the list, just as activity opened.
				String readableId = cursor.getString(cursor.getColumnIndex("readable_id"));
				launchVideoDetailActivity(readableId, topicId);
			}
		}
	};
	
	private void launchVideoDetailActivity(String videoId, String parentTopicId) {
		Intent intent = new Intent(this, VideoDetailActivity.class);
		intent.putExtra(PARAM_VIDEO_ID, videoId);
		if (parentTopicId != null) {
			intent.putExtra(PARAM_TOPIC_ID, parentTopicId);
		}
		startActivity(intent);
	}
	
	private void launchListActivity(String topicId, Class<?> activityClass) {
		Intent intent = new Intent(this, activityClass);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		intent.putExtra(PARAM_SHOW_DL_ONLY, isShowingDownloadedVideosOnly());
		// ALWAYS goes to this topic's parent topic. If this assumption breaks, then we must rethink the clear_top flag.
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}
	
	private void launchHomeActivity() {
		Intent intent = new Intent(this, HomeActivity.class);
		// ALWAYS clear top when going home.
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}
	
	private VideoAdapter getUnwrappedAdapter() {
		if (listView != null) {
			ListAdapter adapter = listView.getAdapter();
			if (adapter instanceof HeaderViewListAdapter) {
				adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
			}
			if (adapter instanceof VideoAdapter) {
				return (VideoAdapter) adapter;
			}
		}
		
		return null;
	}
	
	private void setParentTopic(Topic topic) {
		this.topic = topic;
		
		if (topic != null) {
			topicId = topic.getId();
			
			// header
			((TextView) headerView.findViewById(R.id.header_video_list_title)).setText(topic.getTitle());
			
			String desc = topic.getDescription();
			TextView descView = (TextView) headerView.findViewById(R.id.header_video_list_description);
			if (desc != null && desc.length() > 0) {
				descView.setText(Html.fromHtml(desc).toString());
				descView.setVisibility(View.VISIBLE);
				descView.setMovementMethod(new ScrollingMovementMethod());
			} else {
				descView.setVisibility(View.GONE);
			}
			
			final ImageView thumb = (ImageView) headerView.findViewById(R.id.header_video_list_thumbnail);
			if (thumb != null) {
				new AsyncTask<Void, Void, Bitmap>() {
					@Override public Bitmap doInBackground(Void... arg) {
						Bitmap bmp = thumbnailManager.getThumbnail(VideoListActivity.this.topic.getThumb_id(), Thumbnail.QUALITY_SD);
						return bmp;
					}
					@Override public void onPostExecute(Bitmap bmp) {
						thumb.setImageBitmap(bmp);
					}
				}.execute();
			}

			String countFormat;
			int param;
			if (isShowingDownloadedVideosOnly()) {
				countFormat = getString(R.string.format_downloaded_count);
				param = dataService.getOfflineVideoManager().getDownloadCountForTopic(dataService.getHelper(), topicId, 1);
			} else {
				countFormat = getString(R.string.format_video_count);
				param = topic.getVideo_count();
			}
			((TextView) headerView.findViewById(R.id.header_video_list_count)).setText(String.format(countFormat, param));
			
			listView.setAdapter(new VideoAdapter(this));
			resetListContents(topic.getId());
		}
	}
	
	private void resetListContents(String topicId) {
    	Log.d(LOG_TAG, "resetListContents");
    	
    	if (topicId != null) {
	    	// Set this.topicCursor to a cursor over the videos we need.
	    	User user = dataService.getAPIAdapter().getCurrentUser();
	    	String userId = user == null ? "" : user.getNickname();
	    	
	    	String sql = "select video._id, video.youtube_id, video.readable_id, video.title " +
				    			", uservideo.seconds_watched, uservideo.completed " +
	    			"from topicvideo, video " +
	    			"left outer join uservideo on uservideo.video_id = video.readable_id and uservideo.user_id=? " +
	    			"where topicvideo.topic_id=? and topicvideo.video_id=video.readable_id ";
	    	
	    	String[] selectionArgs;
	    	if (isShowingDownloadedVideosOnly()) {
	    		sql += " and video.download_status=? ";
	    		selectionArgs = new String[] {userId, topicId, String.valueOf(Video.DL_STATUS_COMPLETE)};
	    	} else {
	    		selectionArgs = new String[] {userId, topicId};
	    	}
	    	sql += "order by video.seq";
	    	
			if (topicCursor != null) {
				topicCursor.close();
			}
	    	topicCursor = this.dataService.getHelper().getReadableDatabase()
	    			.rawQuery(sql, selectionArgs);
	    	
	    	CursorAdapter adapter = getUnwrappedAdapter();
	    	if (adapter != null) {
		    	adapter.changeCursor(topicCursor);
	    	}
    	}
	}
	
	public boolean isShowingDownloadedVideosOnly() {
		return isShowingDownloadedVideosOnly;
	}
	
	protected Where<Video, String> addToQuery(Where<Video, String> where) throws SQLException {
		if (isShowingDownloadedVideosOnly()) {
			// This causes non-downloaded videos not to appear in the list. To just disable them, use the VideoAdapter instead.
			where.and().eq("download_status", Video.DL_STATUS_COMPLETE);
		}
		return where;
	}
	
	// map of id:progressbar; put in onBindView
	
	// update on download progress updates
	
	private static class Renderer extends ThumbnailViewRenderer {
		
		private final CursorAdapter mAdapter;
		private int titleColumn, watchedColumn, completedColumn;
		private boolean prepared = false;
		private Map<String, Integer> currentDownloadStatus = new HashMap<String, Integer>();
		
		public Renderer(android.support.v4.widget.CursorAdapter adapter,
				ThumbnailManager thumbnailManager, int cacheCapacity) {
			super(2, R.id.thumbnail, thumbnailManager, Thumbnail.QUALITY_HIGH, cacheCapacity);
			mAdapter = adapter;
		}
		
		@Override
		protected void prepare(View view, Param param, int immediatePassHint) {
			super.prepare(view, param, immediatePassHint);
			
			Cursor cursor = (Cursor) mAdapter.getItem(param.cursorPosition);
			
			if (!prepared) {
				titleColumn = cursor.getColumnIndex("title");
				watchedColumn = cursor.getColumnIndex("seconds_watched");
				completedColumn = cursor.getColumnIndex("completed");
				prepared = true;
			}
			
			String title = cursor.getString(titleColumn);
			
			TextView titleView = (TextView) view.findViewById(R.id.list_video_title);
			ImageView iconView = (ImageView) view.findViewById(R.id.complete_icon);
			ProgressBar bar = (ProgressBar) view.findViewById(R.id.list_video_dl_progress);
			String youtubeId = param.youtubeId;
			bar.setTag(youtubeId);
			updateBar(bar);
			
			// User view completion icon.
			int watched = 0;
			boolean complete = false;
			try {
				watched = cursor.getInt(watchedColumn);
				complete = cursor.getInt(completedColumn) != 0;
			} catch (Exception e) {
				// Swallow. This will be due to null values not making their way through getInt in some implementations.
			}
			int resId = complete
					? R.drawable.video_indicator_complete
					: watched > 0
					? R.drawable.video_indicator_started
					: R.drawable.empty_icon;
					
			iconView.setImageResource(resId);
			titleView.setText(title);
		}
		
		public void onCursorChanged() {
			prepared = false;
		}
		
		public void updateBar(ProgressBar bar) {
			String youtubeId = (String) bar.getTag();
			Integer progress = currentDownloadStatus.get(youtubeId);
			if (progress == null) {
				bar.setVisibility(View.GONE);
			} else {
				switch (progress) {
				case 100:
					bar.setVisibility(View.GONE);
					break;
				case 0:
					bar.setIndeterminate(true);
					bar.setVisibility(View.VISIBLE);
					break;
				default:
					bar.setIndeterminate(false);
					bar.setProgress(progress);
					bar.setVisibility(View.VISIBLE);
				}
			}
		}
		
		public void setStatus(Map<String, Integer> status) {
			currentDownloadStatus = status;
		}
		
	}

	class VideoAdapter extends CursorAdapter {
		
		private final LayoutInflater inflater;
		private final Renderer renderer;
		
		private final ArrayList<ProgressBar> bars = new ArrayList<ProgressBar>();
		
		public VideoAdapter(Context context) {
			super(context, null, 0);
			inflater = LayoutInflater.from(context);
			
			Runtime rt = Runtime.getRuntime();
			long maxMemory = rt.maxMemory();
			Log.v(LOG_TAG, "maxMemory:" + Long.toString(maxMemory));
			
			// Want to use at most about 1/2 of available memory for thumbs.
			// In SAT Math category (116 videos), with a heap size of 48MB, this setting
			// allows 109 thumbs to be cached resulting in total heap usage around 34MB.
			long usableMemory = maxMemory / 2;
			
			// Higher dpi devices use more memory for other things, so we will have a smaller thumb cache.
			// Fire HD 7 is 216dpi, 8.9 is 254, transformer is 150, majority of devices <= 256, occasional ~326, one outlier at 440.
			// On transformer, a cache size of maxMemory / 2 was comfortable, so for now we'll try scaling from there.
			// This yields a max count of about 24 thumbs on Fire HD 7, 36 on transformer.
			usableMemory /= getResources().getDisplayMetrics().density;
			
			int thumbSize = 480 * 360 * 4; // QUALITY_HIGH at 4 bytes per pixel
			int maxCachedCount = (int) (usableMemory / thumbSize);
			
			renderer = new Renderer(this, thumbnailManager, maxCachedCount);
		}
		
		public void setStatus(Map<String, Integer> status) {
			renderer.setStatus(status);
		}
		
		private void updateBars() {
			for (ProgressBar bar : bars) {
				renderer.updateBar(bar);
			}
		}
	
		
		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			renderer.renderView(view, new Param(cursor.getPosition(), cursor.getString(cursor.getColumnIndex("youtube_id"))));
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup root) {
			View view = inflater.inflate(R.layout.list_video, root, false);
			ProgressBar bar = (ProgressBar) view.findViewById(R.id.list_video_dl_progress);
			bars.add(bar);
			return view;
		}
		
		@Override
		public boolean areAllItemsEnabled() {
			// When showing downloaded videos only, we eliminate them from the query instead of disabling them.
			return true;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return true;
		}
		
		@Override
		public void changeCursor(Cursor newCursor) {
			super.changeCursor(newCursor);
			renderer.onCursorChanged();
			updateBars();
		}
	
	}
	
	
}
