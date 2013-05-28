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
package com.concentricsky.android.khanacademy.views;

import java.util.Formatter;
import java.util.Locale;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.util.Log;

public class VideoController extends RelativeLayout implements
		MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

	public static final String LOG_TAG = VideoController.class.getSimpleName();

	// constants for use with hideAfter / showUntil
	public static final int DEFAULT_SHOW_DURATION = 3000;
    public static final int NEVER = -1;
    public static final int NOW = 0;

    private StringBuilder stringBuilder;
    private Formatter stringFormatter;
    private TextView time;
    private ImageButton button;
    private View buttonContainer;
    private ImageButton fullscreenButton;
    private SeekBar seekBar;
    private VideoView video;
    private int duration;
    private Callbacks callbacks;
    
    public interface Callbacks {
        public void onVideoStarted();
        public void onVideoStopped();
        public void onVideoCompleted();
    }
    
    public void setCallbacks(Callbacks callbacks) {
    	this.callbacks = callbacks;
    }
    
    private void doVideoStarted() {
    	if (callbacks != null) {
    		callbacks.onVideoStarted();
    	}
    }
    
    private void doVideoStopped() {
    	if (callbacks != null) {
    		callbacks.onVideoStopped();
    	}
    }
    
    private void doVideoCompleted() {
    	if (callbacks != null) {
    		callbacks.onVideoCompleted();
    	}
    }
    
    private FullscreenRequestHandler fullscreenHandler;
    
    public interface FullscreenRequestHandler {
    	public void onFullscreenToggleRequested();
    }
    
    public void setFullscreenRequestHandler(FullscreenRequestHandler handler) {
    	fullscreenHandler = handler;
    }
    
    private void doFullscreenToggleRequested() {
    	if (fullscreenHandler != null) {
    		fullscreenHandler.onFullscreenToggleRequested();
    	}
    }
    
    private SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && video != null) {
                updateTime(progress);
                video.seekTo(progress);
            }
        }
    
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            showUntil(NEVER);
        }
    
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int dur = video != null && video.isPlaying() ? DEFAULT_SHOW_DURATION : NEVER;
            showUntil(dur);
        }
    };


    public VideoController(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public VideoController(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoController(Context context) {
        super(context);
        init();
    }
    
    private void init() {
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        time = (TextView) findViewById(R.id.controller_time);
        button = (ImageButton) findViewById(R.id.controller_button);
        buttonContainer = findViewById(R.id.controller_button_container);
        fullscreenButton = (ImageButton) findViewById(R.id.fullscreen_button);
        seekBar = (SeekBar) findViewById(R.id.controller_seek_bar);
        seekBar.setOnSeekBarChangeListener(seekBarListener);
        seekBar.setMax(0);
        seekBar.setProgress(0);
        seekBar.setSecondaryProgress(0);
        duration = 0;
        updateTime(0);
        showPlayButton();
    }

    public void setVideoView(VideoView video) {
        this.video = video;
    }

    public void play() {
        if (video != null) {
            video.start();
            doVideoStarted();
            showPauseButton();
            startTrackingVideo();
//            showUntil(DEFAULT_SHOW_DURATION);
        }
    }

    public void pause() {
        if (video != null) {
            video.pause();
            doVideoStopped();
            showPlayButton();
            stopTrackingVideo();
//            showUntil(NEVER);
        }
    }

    public void togglePlay() {
        if (video != null) {
        	if (video.isPlaying()) {
	            pause();
	        } else {
	            play();
	        }
        }
    }

    private void showPlayButton() {
        button.setBackgroundResource(R.drawable.av_play_over_video);
    }

    private void showPauseButton() {
        button.setBackgroundResource(R.drawable.av_pause_over_video);
    }
    
    private void showFullscreenButton() {
    	if (fullscreenButton != null) {
	    	fullscreenButton.setBackgroundResource(R.drawable.av_full_screen);
    	}
    }
    
    private void showFullscreenExitButton() {
    	if (fullscreenButton != null) {
	    	fullscreenButton.setBackgroundResource(R.drawable.av_return_from_full_screen);
    	}
    }
    
    public void setFullscreen(boolean isFullscreen) {
    	if (isFullscreen) {
    		showFullscreenExitButton();
    	} else {
    		showFullscreenButton();
    	}
    }

    public void updateBar() {
    	Log.d(LOG_TAG, "updateBar");
        if (video != null) {
            int position = video.getCurrentPosition();
            seekBar.setProgress(position);
            seekBar.setSecondaryProgress((int) (video.getBufferPercentage() / 100.0 * duration));
            updateTime(position);
        }
    }

    private Runnable updateBarRunnable = new Runnable() {
        @Override
        public void run() {
            updateBar();
            if (video != null && video.isPlaying()) {
                postDelayed(updateBarRunnable, 1000);
            }
        }
    };

    private void startTrackingVideo() {
        post(updateBarRunnable);
    }

    private void stopTrackingVideo() {
        removeCallbacks(updateBarRunnable);
    }
    
    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        if (stringBuilder == null) {
	        stringBuilder = new StringBuilder();
        }
        if (stringFormatter == null) {
	        stringFormatter = new Formatter(stringBuilder, Locale.getDefault());
        }
        stringBuilder.setLength(0);
        if (hours > 0) {
            return stringFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return stringFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private void updateTime(int position) {
        time.setText(stringForTime(position));
    }

    @Override
    protected void onDetachedFromWindow() {
    	Log.d(LOG_TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        removeCallbacks(updateBarRunnable);
        removeCallbacks(hideRunnable);
        seekBar.setOnSeekBarChangeListener(null);
        buttonContainer.setOnClickListener(null);
        if (fullscreenButton != null) {
	        fullscreenButton.setOnClickListener(null);
        }
        
        updateBarRunnable = null;
        hideRunnable = null;
        seekBarListener = null;
        buttonListener = null;
        fullscreenButtonListener = null;
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
    	Log.d(LOG_TAG, "onWindowFocusChanged: " + hasWindowFocus);
    	super.onWindowFocusChanged(hasWindowFocus);
    	
    	if (hasWindowFocus) {
    		startTrackingVideo();
    	} else {
    		stopTrackingVideo();
	        removeCallbacks(hideRunnable);
    	}
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        updateBar();
        showPlayButton();
        stopTrackingVideo();
        doVideoStopped();
        doVideoCompleted();
//        showUntil(NEVER);
    }
    
    private View.OnClickListener buttonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            togglePlay();
        }
    };
    
    private View.OnClickListener fullscreenButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			doFullscreenToggleRequested();
		}
	};

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (video != null) {
            duration = video.getDuration();
            buttonContainer.setOnClickListener(buttonListener);
            if (fullscreenButton != null) {
	            fullscreenButton.setOnClickListener(fullscreenButtonListener);
            }
            seekBar.setMax(duration);
            updateBar();
        }
    }

    public void show() {
    	setVisibility(View.VISIBLE);
    }
    
    public void showUntil(int ms) {
    	show();
        hideAfter(ms);
    }
    
    public void hideAfter(int ms) {
        removeCallbacks(hideRunnable);
        if (ms != NEVER) {
            postDelayed(hideRunnable, ms);
        }
    }
    
    public void hide() {
        setVisibility(View.GONE);
    }

    private Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(LOG_TAG, "hideRunnable");
            hide();
        }
    };

}
