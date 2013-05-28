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

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import android.app.DownloadManager;
import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.views.VideoController;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

/**
 * This holds a Video model and plays its video. 
 * 
 * Offers a callback interface for start/stop events, position updates, and fullscreen requests.
 */
public class VideoFragment extends Fragment
		implements VideoController.Callbacks {
    
    public static final String LOG_TAG = VideoFragment.class.getSimpleName();
    
    public interface Callbacks {
        public void onVideoStarted();
        public void onVideoStopped();
        public void onVideoPrepared();
        public void onVideoCompleted();
        public void onPositionUpdate(int ms);
        public void onFullscreenToggleRequested();
    }
    
    private Handler handler = new Handler();
    private VideoView videoView;
    private VideoController controls;
    private Video video;
    
    private ProgressBar loadingIndicator;
    private View errorView;
    private TextView errorText;
    private String errorMissingVideo;
    
    private final List<Callbacks> callbacks = new ArrayList<Callbacks>();
    
    private class PositionUpdater implements Runnable {
        private boolean running = true;
        @Override
		public void run() {
            if (videoView != null) {
                doPositionUpdate(videoView.getCurrentPosition());
            }
            if (running) {
                handler.postDelayed(this, Constants.POSITION_UPDATE_DELAY);
            }
        }
        public void stop() {
            running = false;
            handler.removeCallbacks(this);
        }
        public void start() {
            running = true;
            handler.removeCallbacks(this);
            handler.post(this);
        }
    };
    private PositionUpdater positionUpdater = new PositionUpdater();
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        errorMissingVideo = getString(R.string.err_video_unavailable);
    }
    
    @Override
    public void onDestroy() {
        unregisterCallbacks();
        super.onDestroy();
    }
	
	/*
	 * Errors: (returnCode, errorCode)
	 * 
	 * https://github.com/android/platform_external_opencore/blob/master/pvmi/pvmf/include/pvmf_return_codes.h
	 * 
	 * (1,-12) overflow
	 * (100, 0)
	 * (1, -2147483648) // read in a so post this might be invalid stream type
	 *                  // or Content-Length header > MAX_INT: http://code.google.com/p/android/issues/detail?id=8624
	 * (-38, 0)			// http://lab-programming.blogspot.com/2012/01/how-to-work-around-android-mediaplayer.html
	 * (1, -1004) // io error, possibly 500 response, see http://stackoverflow.com/a/8244780/931277
	 * 				// Got this one when seeking past the downloaded portion of a partially finished download.
	 * 
	 * Names of some codes between -1000 and -1014: http://android.joao.jp/2011/07/mediaplayer-errors.html
	 * Interesting - says to close AssetFileDescriptor; may be similar with ParcelFileDescriptor, eh? http://stackoverflow.com/a/11069306/931277
	 * 
	 * Out of memory with about 20 video detail views stacked up.
	 */
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
    	ViewGroup container = (ViewGroup) inflater.inflate(R.layout.fragment_video, root, false);
        videoView = (VideoView) container.findViewById(R.id.videoView);
        
        controls = (VideoController) container.findViewById(R.id.controller);
        controls.setVideoView(videoView);
        controls.setCallbacks(this);
        controls.setFullscreenRequestHandler(new VideoController.FullscreenRequestHandler() {
        	@Override
        	public void onFullscreenToggleRequested() {
        		for (Callbacks c : callbacks) {
        			c.onFullscreenToggleRequested();
        		}
        	}
        });
        videoView.setOnCompletionListener(controls);
        
        loadingIndicator = new ProgressBar(getActivity());
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        container.addView(loadingIndicator, p);
        loadingIndicator.setVisibility(View.GONE);
        
        errorView = inflater.inflate(R.layout.missing_video, container, false);
        errorText = (TextView) errorView.findViewById(R.id.text_missing_video);
        container.addView(errorView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        errorView.setVisibility(View.GONE);
        
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				int seekTo = getArguments().getInt(Constants.PARAM_VIDEO_POSITION, 0);
				boolean playing = getArguments().getBoolean(Constants.PARAM_VIDEO_PLAY_STATE, false);
				
				seekTo(seekTo);
				loadingIndicator.setVisibility(View.GONE);
				controls.onPrepared(mp);
				
				for (Callbacks c : callbacks) {
					c.onVideoPrepared();
				}
				
				if (playing) {
					controls.play();
				}
					
			}
		});

        positionUpdater.start();
        return container;
        
        // Setting the following listener causes a crash on Fire HD. For some reason, orientation changes occasionally (usually within about 20 tries)
        // yield the following stack trace, then the device reboots.
        /*
         *  01-09 17:02:33.537: W/HardwareRenderer(3319): EGL error: EGL_BAD_NATIVE_WINDOW
			01-09 17:02:33.576: W/HardwareRenderer(3319): Mountain View, we've had a problem here. Switching back to software rendering.
			01-09 17:02:33.584: E/ViewRootImpl(3319): IllegalArgumentException locking surface
			01-09 17:02:33.584: E/ViewRootImpl(3319): java.lang.IllegalArgumentException
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.view.Surface.lockCanvasNative(Native Method)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.view.Surface.lockCanvas(Surface.java:76)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.view.ViewRootImpl.draw(ViewRootImpl.java:1959)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.view.ViewRootImpl.performTraversals(ViewRootImpl.java:1647)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.view.ViewRootImpl.handleMessage(ViewRootImpl.java:2462)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.os.Handler.dispatchMessage(Handler.java:99)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.os.Looper.loop(Looper.java:137)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at android.app.ActivityThread.main(ActivityThread.java:4486)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at java.lang.reflect.Method.invokeNative(Native Method)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at java.lang.reflect.Method.invoke(Method.java:511)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:784)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:551)
			01-09 17:02:33.584: E/ViewRootImpl(3319): 	at dalvik.system.NativeStart.main(Native Method)
			01-09 17:02:34.654: E/InputQueue-JNI(3319): channel '4181fe60 com.concentricsky.android.khanacademy/com.concentricsky.android.khanacademy.app.VideoListActivity (client)' ~ Publisher closed input channel or an error occurred.  events=0x8
			01-09 17:02:34.662: E/InputQueue-JNI(3319): channel '41841178 com.concentricsky.android.khanacademy/com.concentricsky.android.khanacademy.app.VideoDetailActivity (client)' ~ Publisher closed input channel or an error occurred.  events=0x8
			01-09 17:02:34.662: E/InputQueue-JNI(3319): channel '417e04b0 com.concentricsky.android.khanacademy/com.concentricsky.android.khanacademy.app.HomeActivity (client)' ~ Publisher closed input channel or an error occurred.  events=0x8
         */
        // This happens even without the call to `error`, which shows a Toast. 
        // Looking at the 4.0.3 source on Grepcode, I cannot see how this listener could cause such a problem. I can only guess there is a bug in Amazon's
        // VideoView implementation. The one thing that Google's implementation does before calling the provided listener is to set VideoView#mCurrentState
        // to STATE_ERROR. It's possible that setting an error listener overwrites the default one in Amazon's implementation, removing some critical piece
        // of functionality. Just a guess.
//        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
//			@Override
//			public boolean onError(MediaPlayer mp, int what, int extra) {
//				Log.e(LOG_TAG, String.format("Error: (%d,%d)", what, extra));
//				switch (what) {
//				case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
//				case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
//				case MediaPlayer.MEDIA_ERROR_UNKNOWN:
////					error(errorVideoPlayback);
//				}
//				return false;
//			}
//		});
    }
    
    @Override
	public void onPause() {
    	super.onPause();
    	positionUpdater.stop();
    }
    
    @Override
    public void onResume() {
    	if (positionUpdater != null) {
    		positionUpdater.start();
    	}
    	super.onResume();
    }
    
    @Override
    public void onDestroyView() {
    	Log.d(LOG_TAG, "onDestroyView");
    	dispose();
        super.onDestroyView();
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.v(LOG_TAG, ".onActivityCreated");
        
        final String videoId = getArguments().getString(Constants.PARAM_VIDEO_ID);
        Log.d(LOG_TAG, " videoId is " + videoId);

        ((KADataService.Provider) getActivity()).requestDataService(
                new ObjectCallback<KADataService>() {
                    @Override
					public void call(KADataService dataService) {
                        try {
                            Dao<Video, String> videoDao = dataService.getHelper().getVideoDao();
							QueryBuilder<Video, String> q = videoDao.queryBuilder();
							q.where().eq("readable_id", videoId);
							Video video = videoDao.queryForFirst(q.prepare());
                            setVideo(video);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
    }
    
    /**
     * Set the current video. This 
     * @param video
     */
    private void setVideo(Video video) {
        
    	this.video = video;
    	String url = null;
    	
        DownloadManager dlm = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(video.getDlm_id());
        q.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
        Cursor c = dlm.query(q);
        if (c.moveToFirst()) {
        	String filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
        	if (new File(filename).exists()) {
	        	url = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        	}
        }
        c.close();
        
        if (url == null) {
	        url = video.getMp4url();
        }
        if (url == null) {
            url = video.getM3u8url();
        }

        if (url != null) {
        	Log.d(LOG_TAG, "setVideo: " + url);
            loadingIndicator.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(url));
        } else {
        	error(errorMissingVideo);
        }
    }
    
    private void error(String msg) {
    	if (loadingIndicator != null) {
    		loadingIndicator.setVisibility(View.GONE);
    	}
    	if (videoView != null) {
    		videoView.stopPlayback();
    	}
    	if (errorText != null) {
    		errorText.setText(msg);
    		errorView.setVisibility(View.VISIBLE);
    	}
    	Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
    }
    
    public boolean isPlaying() {
    	if (videoView == null) {
    		return false;
    	}
    	return videoView.isPlaying() && videoView.getCurrentPosition() < videoView.getDuration();
    }
    
    public int getVideoPosition() {
    	if (videoView == null) {
    		return 0;
    	}
    	return videoView.getCurrentPosition();
    }
    
    public void stop() {
    	if (videoView != null) {
	    	videoView.stopPlayback();
    	}
    }
    
    public void play() {
    	if (controls != null) {
    		controls.play();
    	}
    }
    
    public void seekTo(int position) {
    	if (videoView != null) {
    		videoView.seekTo(position);
    		
	    	if (controls != null) {
	    		controls.updateBar();
	    	}
    	}
    }
    
    private void doPositionUpdate(int ms) {
        for (Callbacks c : callbacks) {
            c.onPositionUpdate(ms);
        }
    }
    
    public void registerCallbacks(Callbacks c) {
        callbacks.add(c);
    }
    
    public void unregisterCallbacks() {
        callbacks.clear();
    }
    
    public void unregisterCallbacks(Callbacks c) {
        callbacks.remove(c);
    }

    public void onPositionRequested(int ms) {
        if (videoView != null) {
            int curr = videoView.getCurrentPosition();
            if (ms > curr && videoView.canSeekForward() ||
                    ms < curr && videoView.canSeekBackward()) {
                seekTo(ms);
            }
//            controls.show();
        }
    }
    
    public void dispose() {
    	Log.d(LOG_TAG, "dispose");
    	stop();
    	
    	if (controls != null) {
	    	controls.setCallbacks(null);
	    	controls.setFullscreenRequestHandler(null);
	        controls.setVideoView(null);
	        controls = null;
    	}
        
    	if (videoView != null) {
	        videoView.setMediaController(null);
	        videoView.setOnCompletionListener(null);
	        videoView.setOnPreparedListener(null);
	        videoView.setOnErrorListener(null);
	        if (videoView.isPlaying()) {
		        videoView.stopPlayback();
	        }
	        videoView = null;
    	}
        
    	unregisterCallbacks();
    }

	public float getPercentWatched() {
		if (video == null || videoView == null) {
			return 0;
		}
		int posSeconds = getSecondsWatched();
		return (float) posSeconds / video.getDuration() * 100;
	}
	
	public int getSecondsWatched() {
		if (video == null || videoView == null) {
			return 0;
		}
		return videoView.getCurrentPosition() / 1000;
	}
	
	public int getClampedSecondsWatchedSince(int since) {
		Time now = new Time();
		now.setToNow();
		int secondsNow = (int) (now.toMillis(true) / 1000);
		int maxSeconds = Math.max(secondsNow - since, 0);
		return Math.min(maxSeconds, getSecondsWatched());
	}

	@Override
	public void onVideoStarted() {
		for (Callbacks c : callbacks) {
			c.onVideoStarted();
		}
	}

	@Override
	public void onVideoStopped() {
		for (Callbacks c : callbacks) {
			c.onVideoStopped();
		}
	}
	
	@Override
	public void onVideoCompleted() {
		for (Callbacks c : callbacks) {
			c.onVideoCompleted();
		}
	}
    
}
