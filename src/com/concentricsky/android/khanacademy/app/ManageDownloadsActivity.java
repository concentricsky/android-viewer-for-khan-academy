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
import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_ID;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.KADataService.ServiceUnavailableException;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.Thumbnail;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.util.ThumbnailManager;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer.Param;
import com.j256.ormlite.dao.Dao;

public class ManageDownloadsActivity extends KADataServiceProviderActivityBase {
	
	public static final String LOG_TAG = ManageDownloadsActivity.class.getSimpleName();
	
	// TODO : saveInstanceState

	private GridView gridView;
	private KADataService dataService;
	private ActionMode actionMode;
	private LocalBroadcastManager broadcastManager;
	private CursorAdapter displayOptionsAdapter;
	private Menu menu;
	
	/** Some topics share titles (but not parent hierarchies). Since we display only titles here,
	 *  merging their children is less confusing than listing the same title twice. */
	private String topicTitleFilter;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_LIBRARY_UPDATE.equals(intent.getAction())) {
				Log.d(LOG_TAG, "library update broadcast received");
				// TODO
			} else if (ACTION_BADGE_EARNED.equals(intent.getAction()) && dataService != null) {
				Badge badge = (Badge) intent.getSerializableExtra(EXTRA_BADGE);
				dataService.getAPIAdapter().toastBadge(badge);
			} else if (Constants.ACTION_OFFLINE_VIDEO_SET_CHANGED.equals(intent.getAction())) {
				((CursorAdapter) gridView.getAdapter()).changeCursor(getCursor());
				setCancelButtonEnabled(areDownloadsEnqueued());
				setupListNavigation();
			} else if (ACTION_DOWNLOAD_PROGRESS_UPDATE.equals(intent.getAction())) {
				@SuppressWarnings("unchecked")
				Map<String, Integer> status = (Map<String, Integer>) intent.getSerializableExtra(EXTRA_STATUS);
				Adapter adapter = (Adapter) gridView.getAdapter();
				adapter.setCurrentDownloadStatus(status);
				adapter.updateBars();
			} else if (ACTION_TOAST.equals(intent.getAction())) {
				Toast.makeText(ManageDownloadsActivity.this, intent.getStringExtra(EXTRA_MESSAGE), Toast.LENGTH_SHORT).show();
			}
		}
		
	};
	
	private ActionBar.OnNavigationListener navListener = new ActionBar.OnNavigationListener() {
		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			Log.d(LOG_TAG, "onNavigationItemSelected: " + itemPosition + ", " + itemId);
			
			if (itemId < 0) {
				filterByTopicTitle(null);
			} else {
				Cursor c = (Cursor) displayOptionsAdapter.getItem(itemPosition);
				String topicTitle = c.getString(c.getColumnIndex("title"));
				filterByTopicTitle(topicTitle);
			}
			
			return true;
		}
	};
	
	private AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> grid, View view, int position, long id) {
			// If the list is currently unfiltered, then we query for parents of the video and use the first topic that comes up.
			// Otherwise, we have a topic id.
			Cursor c = (Cursor) grid.getItemAtPosition(position);
			
			String videoId = c.getString(c.getColumnIndex("readable_id"));
			Video video = new Video();
			video.setReadable_id(videoId);
			
			String topicId = null;
			if (topicTitleFilter != null) {
				try {
					List<Topic> topics = dataService.getHelper().getTopicDao().queryForEq("title", topicTitleFilter);
					if (topics != null && topics.size() > 0) {
						topicId = topics.get(0).getId();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			
			if (topicId == null) {
				String sql = "select topic_id from topicvideo where video_id=? limit 1";
				String[] selectionArgs = {videoId};
				c = dataService.getHelper().getReadableDatabase().rawQuery(sql, selectionArgs);
				if (c.moveToFirst()) {
					topicId = c.getString(0);
				}
				c.close();
			}
			
			if (topicId != null) {
				launchVideoDetailActivity(video, topicId);
			}
		}
	};
	
	private AbsListView.MultiChoiceModeListener multiChoiceModeListener = new AbsListView.MultiChoiceModeListener() {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			updateTitle(mode);
			return true;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			actionMode = null;
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			Log.d(LOG_TAG, "onCreateActionMode");
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.downloads_actionmode, menu);
	        actionMode = mode;
	        return true;
		}
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			Log.d(LOG_TAG, "onActionItemClicked");
			
	        switch (item.getItemId()) {
	            case R.id.menu_delete:
	                confirmAndDelete();
	                return true;
				case android.R.id.selectAll:
					Log.d(LOG_TAG, "select all");
					selectAll();
					return true;
	            default:
	                return false;
	        }
		}
		
		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
			Log.d(LOG_TAG, "onItemCheckedStateChanged");
			updateTitle(mode);
		}
		
		private void updateTitle(ActionMode mode) {
			int childCount = gridView.getCount();
			int checkedCount = gridView.getCheckedItemCount();
			mode.setTitle(String.format("%d of %d selected.", checkedCount, childCount));
		}
	};
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_manage_downloads);
		broadcastManager = LocalBroadcastManager.getInstance(this);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		gridView = (GridView) findViewById(R.id.grid);
		gridView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
		gridView.setMultiChoiceModeListener(multiChoiceModeListener);
		gridView.setOnItemClickListener(itemClickListener);
		
		View emptyView = getLayoutInflater().inflate(R.layout.listview_empty, null, false);
		((TextView) emptyView.findViewById(R.id.text_list_empty)).setText(R.string.msg_no_downloaded_videos);
		ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		addContentView(emptyView, p);
		
		gridView.setEmptyView(emptyView);
		
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(final KADataService dataService) {
				ManageDownloadsActivity.this.dataService = dataService;
				
				CursorAdapter adapter = new Adapter(ManageDownloadsActivity.this, null, 0, dataService.getThumbnailManager());
				gridView.setAdapter(adapter);
	
				new AsyncTask<Void, Void, Cursor>() {
					@Override
					protected Cursor doInBackground(Void... arg) {
						return getCursor();
					}
					
					@Override
					protected void onPostExecute(Cursor cursor) {
						((CursorAdapter) gridView.getAdapter()).changeCursor(cursor);
					}
				}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				
				final ActionBar ab = getActionBar();
				ab.setDisplayHomeAsUpEnabled(true);
				
				ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
				ab.setTitle("");
				
				setupListNavigation();
				
				// The receiver performs actions that require a dataService, so register it here.
				IntentFilter filter = new IntentFilter();
				filter.addAction(ACTION_LIBRARY_UPDATE);
				filter.addAction(ACTION_BADGE_EARNED);
				filter.addAction(ACTION_OFFLINE_VIDEO_SET_CHANGED);
				filter.addAction(ACTION_DOWNLOAD_PROGRESS_UPDATE);
				filter.addAction(ACTION_TOAST);
				broadcastManager.registerReceiver(receiver, filter);
			}
		});
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.menu = menu;
		getMenuInflater().inflate(R.menu.downloads_menu, menu);
		setCancelButtonEnabled(areDownloadsEnqueued());
		return true;
	}
	
	private void setCancelButtonEnabled(boolean enabled) {
		if (menu != null) {
			menu.findItem(R.id.menu_cancel).setEnabled(enabled).setVisible(enabled);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.menu_cancel:
			confirmAndCancelDownloads();
			return true;
		}
		return false;
	}
	
	@Override
	protected void onStop() {
		getActionBar().setListNavigationCallbacks(null, null);
		
		gridView.setMultiChoiceModeListener(null);
		gridView.setOnItemClickListener(null);
		Adapter adapter = (Adapter) gridView.getAdapter();
		if (adapter != null) {
			Cursor cursor = adapter.getCursor();
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
			adapter.renderer.stop();
			adapter.renderer.clearCache();
		}
		gridView.setAdapter(null);
		
		if (displayOptionsAdapter != null) {
			Cursor cursor = displayOptionsAdapter.getCursor();
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
		
		broadcastManager.unregisterReceiver(receiver);
		super.onStop();
	}
	
	private Cursor getCursor(SQLiteOpenHelper helper, User currentUser, String topicTitle) {
    	String userId = currentUser == null ? "" : currentUser.getNickname();
    	
    	String sql = "select distinct(video._id) as _id, video.youtube_id, video.readable_id, video.title, video.dlm_id " +
			    			", uservideo.seconds_watched, uservideo.completed " +
    			// If we join on topicvideo without filtering to a single topic id, we get a giant n^2 query that takes forever.
    			"from video" + (topicTitle != null ? ", topicvideo, topic "  : " ") +
    			"left outer join uservideo on uservideo.video_id = video.readable_id and uservideo.user_id=? " +
    			"where video.download_status>? ";
    	
    	String[] selectionArgs;
    	if (topicTitle != null) {
    		sql += " and topicvideo.topic_id=topic._id and topic.title=? and topicvideo.video_id=video.readable_id ";
    		selectionArgs = new String[] {userId, String.valueOf(Video.DL_STATUS_NOT_STARTED), topicTitle};
    	} else {
    		selectionArgs = new String[] {userId, String.valueOf(Video.DL_STATUS_NOT_STARTED)};
    	}
    	sql += "order by video.parentTopic_id, video.seq";
    	
    	return helper.getReadableDatabase().rawQuery(sql, selectionArgs);
	}
	
	/**
	 * Build a cursor appropriate for our current state.
	 * 
	 * Uses {@link getCursor} or {@link getCursor} based on the current actionbar nav dropdown selection.
	 * 
	 * @return
	 */
	private Cursor getCursor() {
		return getCursor(dataService.getHelper(), dataService.getAPIAdapter().getCurrentUser(), topicTitleFilter);
	}
	
	private void filterByTopicTitle(String topicTitle) {
		topicTitleFilter = topicTitle;
		
		new AsyncTask<Void, Void, Cursor>() {
			@Override
			protected Cursor doInBackground(Void... arg) {
				Cursor cursor = getCursor();
				return cursor;
			}
			
			@Override
			protected void onPostExecute(Cursor cursor) {
				((CursorAdapter) gridView.getAdapter()).changeCursor(cursor);
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	private void setupListNavigation() {
		if (displayOptionsAdapter == null) {
			displayOptionsAdapter = getDisplayOptionsAdapter(null);
			displayOptionsAdapter.changeCursor(null);
			getActionBar().setListNavigationCallbacks(displayOptionsAdapter, navListener);
		}
		
		new AsyncTask<Void, Void, Cursor>() {
			@Override
			protected Cursor doInBackground(Void... arg0) {
				return getDisplayOptionsCursor(dataService.getHelper());
			}
			
			@Override
			protected void onPostExecute(Cursor cursor) {
				displayOptionsAdapter.changeCursor(cursor);
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
				
	private Cursor getDisplayOptionsCursor(SQLiteOpenHelper helper) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		String sql = "select distinct topic._id as _id, topic.title as title from topic, topicvideo, video where video.download_status>? and topicvideo.video_id=video.readable_id and topicvideo.topic_id=topic._id group by title";
		String[] selectionArgs = {String.valueOf(Video.DL_STATUS_NOT_STARTED)};
		Cursor mainCursor = db.rawQuery(sql, selectionArgs);
		
		sql = "select '-1' as _id, 'All Videos' as title";
		Cursor headerCursor = db.rawQuery(sql, null);
		
		MergeCursor cursor = new MergeCursor(new Cursor[] {headerCursor, mainCursor});
		return cursor;
	}
	
	private CursorAdapter getDisplayOptionsAdapter(Cursor c) {
		String[] from = {"title"};
		int[] to = {android.R.id.text1};
		return new SimpleCursorAdapter(getActionBar().getThemedContext(), android.R.layout.simple_list_item_1, c, from, to, 0);
	}
	
	private void selectAll() {
		int n = gridView.getCount();
		for (int i = 0; i < n; ++i) {
			gridView.setItemChecked(i, true);
		}
	}
	
	private void confirmAndDelete() {
		View contentView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null, false);
		ListView list = (ListView) contentView.findViewById(R.id.dialog_confirm_delete_list);
		
		ArrayList<String> titles = new ArrayList<String>();
		final HashSet<Video> videos = new HashSet<Video>();
		
		SparseBooleanArray positions = gridView.getCheckedItemPositions();
		int n = positions.size();
		for (int i = 0; i < n; ++i) {
			Cursor c = (Cursor) gridView.getItemAtPosition(positions.keyAt(i));
			Video v = new Video();
			v.setReadable_id(c.getString(c.getColumnIndex("readable_id")));
			v.setYoutube_id(c.getString(c.getColumnIndex("youtube_id")));
			v.setDlm_id(c.getLong(c.getColumnIndex("dlm_id")));
			videos.add(v);
			titles.add(c.getString(c.getColumnIndex("title")));
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.dialog_confirm_delete_item);
		adapter.addAll(titles);
		list.setAdapter(adapter);
		
	    new AlertDialog.Builder(this)
	    .setView(contentView)
        .setMessage(getString(R.string.msg_delete_videos))
        .setPositiveButton(getString(R.string.button_confirm_delete), new DialogInterface.OnClickListener() {
        	@Override
            public void onClick(DialogInterface dialog, int id) {
        		deleteItems(videos);
        		if (actionMode != null) {
        			actionMode.finish();
        		}
            }
        })
        .setNegativeButton(getString(R.string.button_cancel), null)
        .show();
	}
	
	private void confirmAndCancelDownloads() {
	    new AlertDialog.Builder(this)
        .setMessage(getString(R.string.msg_cancel_downloads))
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
        	@Override
            public void onClick(DialogInterface dialog, int id) {
        		dataService.getOfflineVideoManager().cancelAllVideoDownloads();
            }
        })
        .show();
	}
	
	private void deleteItems(Set<Video> videos) {
		dataService.getOfflineVideoManager().deleteOfflineVideos(videos);
	}
	
	private void launchVideoDetailActivity(Video video, String parentTopicId) {
		// This may be a video in a different topic from where the user came from.
		// We synthesize the correct back stack for this video.
		
		Stack<Topic> stack = new Stack<Topic>();
		Topic topic = null;
		try {
			Dao<Topic, String> topicDao = getDataService().getHelper().getTopicDao();
			topic = topicDao.queryForId(parentTopicId);
			while (topic != null) {
				stack.push(topic);
				topicDao.refresh(topic);
				topic = topic.getParentTopic();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ServiceUnavailableException e) {
			e.printStackTrace();
		}
		
		TaskStackBuilder t = TaskStackBuilder.create(this);
		Intent intent;
		while (!stack.isEmpty()) {
			topic = stack.pop();
			if (topic.getParentTopic() == null) {
				// Root topic gets the HomeActivity.
				intent = new Intent(this, HomeActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			} else if (stack.isEmpty()) {
				// The video's immediate parent topic gets a VideoList.
				intent = new Intent(this, VideoListActivity.class);
				intent.putExtra(PARAM_TOPIC_ID, topic.getId());
			} else {
				// All other intermediate topics get TopicLists.
				intent = new Intent(this, TopicListActivity.class);
				intent.putExtra(PARAM_TOPIC_ID, topic.getId());
			}
			t.addNextIntent(intent);
		}
		
		// Add a manage downloads activity also, for back. It is skipped when going "up" from videos.
		intent = new Intent(this, ManageDownloadsActivity.class);
		t.addNextIntent(intent);
		
		intent = new Intent(this, VideoDetailActivity.class);
		intent.putExtra(PARAM_VIDEO_ID, video.getId());
		intent.putExtra(PARAM_TOPIC_ID, parentTopicId);
		t.addNextIntent(intent);
		t.startActivities();
		
		/*
		 	TaskStackBuilder just does this:
		 	
		   intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
               IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
               IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
               
            and then uses Activity#startActivities. Support library fallback pre-honeycomb is to just start the top
            activity and allow back to progress back through the actual back stack (no synthesis).
		 */
		
		
//		startActivity(intent);
	}
	
	private boolean areDownloadsEnqueued() {
		DownloadManager.Query q = new DownloadManager.Query();
		q.setFilterByStatus(DownloadManager.STATUS_PAUSED
				| DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING);
		Cursor c = getDownloadManager().query(q);
		boolean result = c.getCount() > 0;
		c.close();
		return result;
	}
	
	private DownloadManager getDownloadManager() {
		return (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	private static class Adapter extends CursorAdapter {
		
		private final LayoutInflater inflater;
		private final Renderer renderer;
		private int youtubeIdIndex = -1;
		private final List<ProgressBar> bars = new ArrayList<ProgressBar>();

		public Adapter(Context context, Cursor c, int flags, ThumbnailManager thumbnailManager) {
			super(context, c, flags);
			inflater = LayoutInflater.from(context);
			
			// Want to use at most about 1/2 of available memory for thumbs.
			// In SAT Math category (116 videos), with a heap size of 48MB, this setting
			// allows 109 thumbs to be cached resulting in total heap usage around 34MB.
			long maxMemory = Runtime.getRuntime().maxMemory();
			long usableMemory = maxMemory / 2;
			
			// Higher dpi devices use more memory for other things, so we will have a smaller thumb cache.
			// Fire HD 7 is 216dpi, 8.9 is 254, transformer is 150, majority of devices <= 256, occasional ~326, one outlier at 440.
			// On transformer, a cache size of maxMemory / 2 was comfortable, so for now we'll try scaling from there.
			// This yields a max count of about 72 thumbs on Fire HD 7, 109 on transformer. About 20-27 fit on screen.
			usableMemory /= context.getResources().getDisplayMetrics().density;
			
			int thumbSize = 320 * 180 * 4; // QUALITY_MEDIUM at 4 bytes per pixel
			int maxCachedCount = (int) (usableMemory / thumbSize);
			renderer = new Renderer(this, thumbnailManager, maxCachedCount);
		}

		public void setCurrentDownloadStatus(Map<String, Integer> status) {
			renderer.setCurrentDownloadStatus(status);
		}

		public void updateBars() {
			for (ProgressBar bar : bars) {
				renderer.updateBar(bar);
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View view = inflater.inflate(R.layout.list_video, parent, false);
			ProgressBar bar = (ProgressBar) view.findViewById(R.id.list_video_dl_progress);
			bars.add(bar);
			return view;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			if (youtubeIdIndex < 0) {
				youtubeIdIndex = cursor.getColumnIndex("youtube_id");
			}
			renderer.renderView(view, new Param(cursor.getPosition(), cursor.getString(youtubeIdIndex)));
		}
		
		@Override
		public void changeCursor(Cursor cursor) {
			youtubeIdIndex = -1;
			super.changeCursor(cursor);
			updateBars();
		}
	}
	
	private static class Renderer extends ThumbnailViewRenderer {
		
		private CursorAdapter mAdapter;
		private int titleColumn, watchedColumn, completedColumn;
		private boolean prepared = false;
		private Map<String, Integer> currentDownloadStatus = new HashMap<String, Integer>();

		public Renderer(android.support.v4.widget.CursorAdapter adapter,
				ThumbnailManager thumbnailManager, int cacheCapacity) {
			super(2, R.id.thumbnail, thumbnailManager, Thumbnail.QUALITY_MEDIUM, cacheCapacity);
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
			bar.setTag(param.youtubeId);
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
		
		public void setCurrentDownloadStatus(Map<String, Integer> status) {
			currentDownloadStatus = status;
		}
		
	}
	
}
