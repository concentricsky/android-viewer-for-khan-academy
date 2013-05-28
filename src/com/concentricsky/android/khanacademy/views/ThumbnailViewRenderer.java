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

import java.util.Locale;

import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.concentricsky.android.khanacademy.util.ThumbnailManager;
import com.concentricsky.android.khanacademy.views.ThumbnailViewRenderer.Param;
import com.tomgibara.android.util.ViewRenderer;

public class ThumbnailViewRenderer extends ViewRenderer<Param, Bitmap> {
	
	private final ThumbnailManager mThumbnailManager;
	private final int mThumbViewId;
	private final byte mQuality;
	
	public ThumbnailViewRenderer(int threadCount, int thumbViewId, ThumbnailManager thumbnailManager, byte targetQuality, int cacheCapacity) { 
		super(threadCount, 1, false, cacheCapacity);
		mThumbnailManager = thumbnailManager;
		mThumbViewId = thumbViewId;
		mQuality = targetQuality;
	}
	
	public ThumbnailViewRenderer(int thumbViewId, ThumbnailManager thumbnailManager, byte targetQuality, int cacheCapacity) {
		this(0, thumbViewId, thumbnailManager, targetQuality, cacheCapacity);
	}
	
	@Override
	protected void prepare(View view, Param param, int immediatePassHint) {
		final ImageView thumbView = (ImageView) view.findViewById(mThumbViewId);
		
		thumbView.setAnimation(null);
		if (immediatePassHint < 0) {
			thumbView.setImageBitmap(null);
			
			// Post because running immediately causes the animation to apply to the set(null) above.
			thumbView.post(new Runnable() {
				public void run() {
					final Animation animation = AnimationUtils.loadAnimation(thumbView.getContext(), android.R.anim.fade_in);
					animation.setAnimationListener(new Animation.AnimationListener() {
						public void onAnimationStart(Animation animation) { }
						
						@Override
						public void onAnimationRepeat(Animation animation) { }
						
						@Override
						public void onAnimationEnd(Animation animation) {
							animation.setAnimationListener(null);
							thumbView.setAnimation(null);
						}
					});
					thumbView.setAnimation(animation);
				}
			});
		}
	}

	@Override
	protected Bitmap render(Param param, int pass) {
		String id = getYoutubeId(param);
		return mThumbnailManager.getThumbnail(id, mQuality);
	}
	
	protected String getYoutubeId(Param param) {
		return param.youtubeId;
	}

	@Override
	protected void update(View view, Bitmap render, int pass) {
		final ImageView thumbView = (ImageView) view.findViewById(mThumbViewId);
		thumbView.setImageBitmap(render);
	}
	
	/**
	 * Parameter holder.
	 * 
	 * Previously, we were just using cursor position and looking up youtube id from there.
	 * However, doing this off the UI thread caused issues with cursor synchronization, so
	 * now we hold the youtube id separately. Do not access the cursor on the background
	 * thread.
	 */
	public static class Param {
		public final int cursorPosition;
		public final String youtubeId;
		
		public Param(int cursorPosition, String youtubeId) {
			if (cursorPosition < 0) throw new IllegalArgumentException("negative cursorPosition");
			if (youtubeId == null) throw new IllegalArgumentException("null youtubeId");
			this.cursorPosition = cursorPosition;
			this.youtubeId = youtubeId;
		}
		
		@Override
		public int hashCode() {
			return String.format(Locale.US, "%d%s", cursorPosition, youtubeId).hashCode();
		}
		
		@Override
		public boolean equals(Object that) {
			return that != null && that instanceof Param && that.hashCode() == hashCode();
		}
	}
	
}