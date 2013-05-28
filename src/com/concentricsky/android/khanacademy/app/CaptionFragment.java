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

import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_ID;
import static com.concentricsky.android.khanacademy.Constants.PARAM_VIDEO_POSITION;

import java.sql.SQLException;
import java.util.List;

import android.app.Fragment;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Caption;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

public class CaptionFragment extends Fragment
		implements AbsListView.OnScrollListener {
	
	public static final String LOG_TAG = CaptionFragment.class.getSimpleName();
	public static final int SCROLL_OFFSCREEN_DELAY = 30000;
	public static final int SCROLL_ONSCREEN_DELAY = 5000;
	public static final int NEVER = -1;
	public static final int NOW = 0;
	
	public interface Callbacks {
		public void onPositionRequested(int ms);
		public void onDownloadRequested(Video video);
		public void onCaptionsUnavailable();
		public void onCaptionsLoaded();
	}
	
	// For use in AsyncTasks, which should not do most things after the activity/fragment has been destroyed.
	private boolean destroyed = false;
	
	private LinearLayout containerView;
	private View loadingView;
	private View emptyView;
	private ListView listView;
	
	private Video video;
	private Callbacks callbacks;
	private List<Caption> captions;
	private boolean tracking = true;
	private int currentPosition = 0;
	private Runnable startTracking = new Runnable() {
		@Override
		public void run() {
			tracking = true;
		}
	};
	private Handler handler = new Handler();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		containerView = (LinearLayout) inflater.inflate(R.layout.fragment_captions, null, false);
		
		loadingView = containerView.findViewById(R.id.loading_captions);
		emptyView = containerView.findViewById(R.id.empty_captions);
		((TextView) emptyView.findViewById(R.id.text_captions_empty)).setText(R.string.msg_captions_loading);
		listView = (ListView) containerView.findViewById(android.R.id.list);
		
		return containerView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		listView.setOnScrollListener(this);
		listView.setBackgroundColor(Color.WHITE);
		listView.setOnItemClickListener(itemClickListener);
		
		// TODO : saved value, default, ...
		Bundle args = getArguments();
		final String videoId = args.getString(PARAM_VIDEO_ID);
		
		((KADataService.Provider) getActivity()).requestDataService(
				new ObjectCallback<KADataService>() {
					@Override
					public void call(KADataService dataService) {
						
						if (destroyed) return;
						
		//				dataService.getAPIAdapter().registerUserUpdateListener(userUpdateListener);
						
						try {
							Dao<Video, String> videoDao = dataService.getHelper().getVideoDao();
							
							QueryBuilder<Video, String> q = videoDao.queryBuilder();
							q.where().eq("readable_id", videoId);
							video = videoDao.queryForFirst(q.prepare());
							
							if (video != null) {
								// Grab captions.
								new CaptionFetcher(dataService).executeOnExecutor(
										AsyncTask.THREAD_POOL_EXECUTOR, video.getYoutube_id());
								// TODO : user points
							}
							
							
							int pos = getArguments().getInt(PARAM_VIDEO_POSITION, 0);
							setCurrentPosition(pos);
						} catch (SQLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
		);
		
	}
	
	@Override
	public void onDestroy() {
		destroyed = true;
		
		// TODO : This may need to go in pause/resume
//			getListView().setOnScrollListener(null);
		super.onDestroy();
	}
	
	class CaptionFetcher extends AsyncTask<String, Void, List<Caption>> {
		
		KADataService dataService;
		
		CaptionFetcher(KADataService dataService) {
			this.dataService = dataService;
		}

		@Override
		protected List<Caption> doInBackground(String... params) {
			String youtubeId = params[0];
			return dataService.getCaptionManager().getCaptions(youtubeId);
		}
		
		@Override
		protected void onPostExecute(List<Caption> result) {
			if (destroyed) return;
			
			if (result != null && result.size() > 0) {
				// We have captions, so we want a ListView.
				ListAdapter adapter = new CaptionAdapter(result);
				
				listView.setAdapter(adapter);
				loadingView.setVisibility(View.GONE);
				emptyView.setVisibility(View.GONE);
				listView.setVisibility(View.VISIBLE);
				if (callbacks != null) {
					callbacks.onCaptionsLoaded();
				}
			} else {
				// No captions. We need a "no captions" message.
				loadingView.setVisibility(View.GONE);
				// TODO : Vary the message for no captions exist / error retrieving captions.
				((TextView) emptyView.findViewById(R.id.text_captions_empty)).setText(R.string.msg_captions_unavailable);
				emptyView.setVisibility(View.VISIBLE);
				listView.setVisibility(View.GONE);
				if (callbacks != null) {
					callbacks.onCaptionsUnavailable();
				}
			}
			captions = result;
		}
	}

	private class CaptionAdapter extends ArrayAdapter<Caption> {
		
		final LayoutInflater inflater;

		public CaptionAdapter(List<Caption> objects) {
			super(getActivity(), R.layout.list_item_caption, objects);
			inflater = getActivity().getLayoutInflater();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				try {
					convertView = inflater.inflate( R.layout.list_item_caption, parent, false);
				} catch (InflateException e) {
					e.printStackTrace();
					return null;
				}
			}
			TextView time = (TextView) convertView.findViewById(R.id.caption_time);
			TextView text = (TextView) convertView.findViewById(R.id.caption_text);
			
			if (time == null || text == null) {
				// Have seen some inexplicable NPEs somewhere in this method (line
				// number reads 368, which was the last line of this file.)
				return convertView;
			}
			
			Caption caption = getItem(position);
			if (caption != null) {
				time.setText(caption.getTime_string());
				text.setText(caption.getText());
			} else {
				time.setText("");
				text.setText("");
			}
			
			return convertView;
		}

		@Override
		public boolean isEmpty() {
			boolean result = captions == null || captions.size() == 0;
			Log.d(LOG_TAG, String.format("Adapter.isEmpty: %b", result));
			return result;
		}
		
	}
	
	public void onVideoStarted() {
		// TODO Auto-generated method stub
		
	}

	public void onVideoStopped() {
		// TODO Auto-generated method stub
		
	}

	public void onPositionUpdate(int ms) {
		int position = getPositionForTime(ms);
		if (position != currentPosition) {
			setCurrentPosition(position);
			if (tracking) {
				trackToPosition(position);
			} else if (isItemVisible(position)) {
				startTrackingIn(SCROLL_ONSCREEN_DELAY);
			}
		}
	}
	
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		Log.d(LOG_TAG, "onScrollStateChanged: " + scrollState);
		
		switch (scrollState) {
		// If user has begun scrolling manually, stop tracking.
		case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
			stopTracking();
			break;
		// If the scroll has finished, start tracking after a delay.
		case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
			startTrackingIn(SCROLL_OFFSCREEN_DELAY);
			break;
		}
	}
	
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		// TODO Auto-generated method stub
		Log.v(LOG_TAG, "onScroll");
	}
	
	private int getPositionForTime(int ms) {
		int result = -1;
		if (captions != null) {
			int N=captions.size();
			for (int i=0; i<N; ++i) {
				Caption c = captions.get(i);
				if (c.getStart_time() < ms || result < 0) {
					result = i;
				} else {
					break;
				}
			}
		}
		return result;
	}
	
	private void setCurrentPosition(int position) {
		currentPosition = position;

		if (listView != null) {
			// uncheck other items
			listView.clearChoices();
			
			// check correct item
			if (position >= 0) {
				listView.setItemChecked(position, true);
			}
		}
	}
	
	private void trackToPosition(int position) {
		// This can happen if we rotate while the video is playing. The caption fragment is still being
		// created, and gets a callback for video position. We do not need to store the seek position
		// and do it once the view is constructed, because the callback comes very often and will happen
		// soon after construction anyway.
		if (listView != null) {
			// I think there was a good reason to use setSelection.. on Nexus 7, but smoothScrollTo... works fine on Fire HD.
//			listView.setSelectionFromTop(position, listView.getMeasuredHeight() / 2);
			listView.smoothScrollToPositionFromTop(position, listView.getMeasuredHeight() / 2);
		}
	}
	
	private boolean isItemVisible(int position) {
		return listView.getFirstVisiblePosition() <= position &&
				position <= listView.getLastVisiblePosition();
	}
	
	private void startTrackingIn(int ms) {
		if (ms >= 0) {
			handler.postDelayed(startTracking, ms);
		}
	}
	
	private void stopTracking() {
		tracking = false;
		handler.removeCallbacks(startTracking);
	}
	
	private AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			startTrackingIn(NOW);
			setCurrentPosition(position);
			if (callbacks != null) {
				int ms = ((Caption) listView.getAdapter().getItem(position)).getStart_time();
				callbacks.onPositionRequested(ms);
			}
		}
	};
	
	public void registerCallbacks(Callbacks callbacks) {
		this.callbacks = callbacks;
	}
	
}
