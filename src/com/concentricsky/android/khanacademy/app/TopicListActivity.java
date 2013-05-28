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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants.Direction;
import com.concentricsky.android.khanacademy.MainMenuDelegate;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Badge;
import com.concentricsky.android.khanacademy.data.db.Thumbnail;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.User;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.util.ThumbnailManager;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer.Param;
import com.j256.ormlite.android.AndroidDatabaseResults;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

public class TopicListActivity extends KADataServiceProviderActivityBase {

	public static final String LOG_TAG = TopicListActivity.class.getSimpleName();
	
	private GridView gridView;
	private View headerView;
	private Topic topic; // Use this instead of topicId so we can hang onto `topic.child_kind' as well.
	private String topicId;
	private Dao<Topic, String> dao;
	private ThumbnailManager thumbnailManager;
	private MainMenuDelegate mainMenuDelegate;
	private Menu mainMenu;
	private KADataService dataService;
	private Cursor topicCursor;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_LIBRARY_UPDATE.equals(intent.getAction()) && topic != null) {
				Log.d(LOG_TAG, "library update broadcast received");
				TopicGridAdapter adapter = getUnwrappedAdapter();
				if (adapter != null) {
					if (topicCursor != null) {
						topicCursor.close();
					}
					topicCursor = buildCursor(topicId);
					adapter.changeCursor(topicCursor);
				}
			} else if (ACTION_BADGE_EARNED.equals(intent.getAction()) && dataService != null) {
				Badge badge = (Badge) intent.getSerializableExtra(EXTRA_BADGE);
				dataService.getAPIAdapter().toastBadge(badge);
			} else if (ACTION_TOAST.equals(intent.getAction())) {
				Toast.makeText(TopicListActivity.this, intent.getStringExtra(EXTRA_MESSAGE), Toast.LENGTH_SHORT).show();
			}
		}
		
	};

	// Used to avoid touching the ui with AsyncTask callbacks after the ui is no longer available.
	boolean stopped = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_topic_list);
		
		// Set / restore state.
		Intent intent = getIntent();
		topicId = intent != null && intent.hasExtra(PARAM_TOPIC_ID)
				? intent.getStringExtra(PARAM_TOPIC_ID)
				: savedInstanceState != null && savedInstanceState.containsKey(PARAM_TOPIC_ID)
				? savedInstanceState.getString(PARAM_TOPIC_ID)
				: null;
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		stopped = false;
		
		mainMenuDelegate = new MainMenuDelegate(this);
		gridView = (GridView) findViewById(R.id.activity_topic_list_grid);
		gridView.setOnItemClickListener(clickListener);
		
		ActionBar ab = getActionBar();
		ab.setDisplayHomeAsUpEnabled(true);
		ab.setTitle("Topics");
		
		requestDataService(new ObjectCallback<KADataService>() {
			@Override
			public void call(final KADataService dataService) {
				TopicListActivity.this.dataService = dataService;
				
				try {
					thumbnailManager = dataService.getThumbnailManager();
					
					dao = dataService.getHelper().getTopicDao();
					if (topicId != null) {
						topic = dao.queryForId(topicId);
					}
					else {
						topic = dataService.getRootTopic();
						topicId = topic.getId();
					}
					
					// DEBUG
					if (topic == null) return;
					
					// header
					headerView = findViewById(R.id.header_topic_list);
		
					((TextView) headerView.findViewById(R.id.header_video_list_title)).setText(topic.getTitle());
					
					String desc = topic.getDescription();
					TextView descView = (TextView) headerView.findViewById(R.id.header_video_list_description);
					if (desc != null && desc.length() > 0) {
						descView.setText(Html.fromHtml(desc));
						descView.setVisibility(View.VISIBLE);
					} else {
						descView.setVisibility(View.GONE);
					}
					
					// Find child count for this parent topic.
					boolean videoChildren = Topic.CHILD_KIND_VIDEO.equals(topic.getChild_kind());
					String[] idArg = {topic.getId()};
					String sql = videoChildren ? "select count() from topicvideo where topic_id=?"
											   : "select count() from topic where parentTopic_id=?";
					int count = (int) dao.queryRawValue(sql, idArg);
					String countFormat = getString(videoChildren ? R.string.format_video_count : R.string.format_topic_count);
					
					// Set header count string.
					((TextView) headerView.findViewById(R.id.header_video_list_count)).setText(
							String.format(countFormat, count));
					
					final ImageView thumb = (ImageView) headerView.findViewById(R.id.header_video_list_thumbnail);
					if (thumb != null) {
						new AsyncTask<Void, Void, Bitmap>() {
							@Override public Bitmap doInBackground(Void... arg) {
								Bitmap bmp = thumbnailManager.getThumbnail(TopicListActivity.this.topic.getThumb_id(), Thumbnail.QUALITY_SD);
								return bmp;
							}
							@Override public void onPostExecute(Bitmap bmp) {
								thumb.setImageBitmap(bmp);
							}
						}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
							
					// list
					if (topicCursor != null) {
						topicCursor.close();
					}
					topicCursor = buildCursor(topicId);
					
					ListAdapter adapter = new TopicGridAdapter(topicCursor);
					gridView.setAdapter(adapter);
					
					// Request the topic and its children be updated from the api.
					List<Topic> children = dao.queryForEq("parentTopic_id", topic.getId());
					List<String> toUpdate = new ArrayList<String>();
					for (Topic child : children) {
						toUpdate.add(child.getId());
					}
					toUpdate.add(topic.getId());
				} catch (SQLException e) {
					e.printStackTrace();
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
	public void onStop() {
		Log.d(LOG_TAG, "onStop");
		stopped = true;
		
		if (gridView != null) {
			gridView.setOnItemClickListener(null);
			TopicGridAdapter adapter = (TopicGridAdapter) gridView.getAdapter();
			if (adapter != null) {
				adapter.changeCursor(null);
				adapter.renderer.stop();
				adapter.renderer.clearCache();
			}
			gridView.setAdapter(null);
			gridView = null;
		}
		
		if (headerView != null) {
			final ImageView thumb = (ImageView) headerView.findViewById(R.id.header_video_list_thumbnail);
//			thumb.setImageResource(0);
			Drawable d = thumb.getDrawable();
			if (d instanceof BitmapDrawable) {
				Bitmap bmp = ((BitmapDrawable) d).getBitmap();
				if (bmp != null) {
					bmp.recycle();
				}
			}
		}
		
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		receiver = null;
		
		super.onStop();
	}
	
	private TopicGridAdapter getUnwrappedAdapter() {
		if (gridView != null) {
			ListAdapter adapter = gridView.getAdapter();
			if (adapter instanceof TopicGridAdapter) {
				return (TopicGridAdapter) adapter;
			}
		}
		
		return null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mainMenu = menu;
		mainMenuDelegate.onCreateOptionsMenu(menu);
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
						launchListActivity(parentTopic.getId(), TopicListActivity.class, Direction.BACKWARD);
					}
				}
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
	    }
	}
	
	private void launchHomeActivity() {
		Intent intent = new Intent(this, HomeActivity.class);
		// ALWAYS clear top when going home.
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}
	
	private void launchListActivity(String topicId, Class<?> activityClass, Direction direction) {
		Intent intent = new Intent(this, activityClass);
		intent.putExtra(PARAM_TOPIC_ID, topicId);
		switch (direction) {
		case BACKWARD:
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			finish();
		default: // nop
		}
		startActivity(intent);
	}
	
	private AdapterView.OnItemClickListener clickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> gridView, View clickedView, int position, long id) {
			Log.d(LOG_TAG, "onItemClick");
			
			// TODO : Header clicks.
			
			// Get the clicked item (a properly positioned cursor).
			Cursor item = (Cursor) gridView.getAdapter().getItem(position);
			String topicId = item.getString(item.getColumnIndex("_id"));
			String kind = item.getString(item.getColumnIndex("child_kind"));
			Log.d(LOG_TAG, String.format("  child_kind is %s", kind));
			
			// Should only ever find CHILD_KIND_TOPIC or CHILD_KIND_VIDEO here, thanks to the query used (see `buildCursor' below).
			Class<?> activityClass = Topic.CHILD_KIND_TOPIC.equals(kind)
					? TopicListActivity.class
					: VideoListActivity.class;
			
			launchListActivity(topicId, activityClass, Direction.FORWARD);
		}
	};
	
	private class Renderer extends ThumbnailViewRenderer {

		private CursorAdapter mAdapter;
		private int titleColumn, childKindColumn, idColumn;
		private boolean prepared = false;
		
		public Renderer(CursorAdapter adapter, ThumbnailManager thumbnailManager, int cacheCapacity) {
			super(2, R.id.pane_topic_image, thumbnailManager, Thumbnail.QUALITY_SD, cacheCapacity);
			mAdapter = adapter;
		}

		@Override
		protected void prepare(View view, Param param, int immediatePassHint) {
			super.prepare(view, param, immediatePassHint);
			
			TextView titleView = (TextView) view.findViewById(R.id.pane_topic_title);
			TextView countView = (TextView) view.findViewById(R.id.pane_topic_video_count);
			TextView descView = (TextView) view.findViewById(R.id.pane_topic_description);
			
			Cursor c = (Cursor) mAdapter.getItem(param.cursorPosition);
			
			if (!prepared) {
				titleColumn = c.getColumnIndex("title");
				childKindColumn = c.getColumnIndex("child_kind");
				idColumn = c.getColumnIndex("_id");
				prepared = true;
			}
			
			String title = c.getString(titleColumn);
			boolean videoChildren = Topic.CHILD_KIND_VIDEO.equals(c.getString(childKindColumn));
			String countFormat = getString(videoChildren ? R.string.format_video_count : R.string.format_topic_count);
			String[] idArg = {c.getString(idColumn)};
			String sql = videoChildren ? "select count() from topicvideo where topic_id=?"
									   : "select count() from topic where parentTopic_id=?";
			int count = 0;
			try {
				// TODO : Denormalize the child count so we don't need to query here.
				count = (int) dao.queryRawValue(sql, idArg);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			String countString = String.format(countFormat, count);
			
			descView.setVisibility(View.GONE);
			titleView.setText(title);
			countView.setText(countString);
		}
		
	}
	
	private class TopicGridAdapter extends SimpleCursorAdapter {
		
		private final Renderer renderer;
		
		TopicGridAdapter(Cursor cursor) {
			super(TopicListActivity.this, R.layout.pane_topic, cursor,
					new String[] {"title"}, new int[] {R.id.pane_topic_title}, 0);
			
			Runtime rt = Runtime.getRuntime();
			long maxMemory = rt.maxMemory();
			
			// Want to use at most about 1/2 of available memory for thumbs.
			long usableMemory = maxMemory / 2;
			
			// Higher dpi devices use more memory for other things, so we will have a smaller thumb cache.
			// Fire HD 7 is 216dpi, 8.9 is 254, transformer is 150, majority of devices <= 256, occasional ~326, one outlier at 440.
			// On transformer, a cache size of maxMemory / 2 was comfortable, so for now we'll try scaling from there.
			// This yields a max count of about 13 thumbs on Fire HD 7, 20 on transformer.
			usableMemory /= getResources().getDisplayMetrics().density;
			
			int thumbSize = 640 * 480 * 4; // QUALITY_SD at 4 bytes per pixel
			int maxCachedCount = (int) (usableMemory / thumbSize);
		
			renderer = new Renderer(this, thumbnailManager, maxCachedCount);
		}
		
		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			
			if (view == null) {
				LayoutInflater inflater = LayoutInflater.from(context);
				view = inflater.inflate(R.layout.pane_topic, null, false);
			}
			
			renderer.renderView(view, new Param(cursor.getPosition(), cursor.getString(cursor.getColumnIndex("thumb_id"))));
		}
		
	}
	
	private Cursor buildCursor(String topicId) {
    	AndroidDatabaseResults iterator = null;
    	
    	try {
    		// select count() from topic, video where video.parentTopic_id=topic._id and video.seq=0;
	    	QueryBuilder<Topic, String> qb = this.dao.queryBuilder();
	    	qb.orderBy("seq", true);
    		Where<Topic, String> where = qb.where();
	    	where.eq("parentTopic_id", topicId).and().gt("video_count", 0).and().in("child_kind", Topic.CHILD_KIND_TOPIC, Topic.CHILD_KIND_VIDEO);
	    	PreparedQuery<Topic> pq = qb.prepare();
	    	iterator = (AndroidDatabaseResults) dao.iterator(pq).getRawResults();
	    	return iterator.getRawCursor();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return null;
	}
	
}
