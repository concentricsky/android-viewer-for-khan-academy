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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.concentricsky.android.khan.R;


public class ThumbnailWrapper extends FrameLayout {
	
	public static final float DEFAULT_RATIO_WIDTH = 16f;
	public static final float DEFAULT_RATIO_HEIGHT = 9f;
	
	private float ratioWidth;
	private float ratioHeight;
	protected float ratio;
	protected int declaredWidth, declaredHeight;

	// Are we maintaining the given aspect ratio, or defaulting to a normal FrameLayout?
	protected boolean maintain = true;

	public ThumbnailWrapper(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		parseAttributes(attrs);
	}

	public ThumbnailWrapper(Context context, AttributeSet attrs) {
		super(context, attrs);
		parseAttributes(attrs);
	}

	public ThumbnailWrapper(Context context) {
		super(context);
	}
	
	private void parseAttributes(AttributeSet attrs) {
		// For use when maintain == false.
		declaredWidth = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "layout_width", ViewGroup.LayoutParams.MATCH_PARENT);
		declaredHeight = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "layout_height", ViewGroup.LayoutParams.MATCH_PARENT);
		
		// Get our custom ratio attributes.
		TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.ThumbnailWrapper);
		int N = a.getIndexCount();
		for (int i = 0; i < N; ++i) {
			int attr = a.getIndex(i);
			switch (attr)
			{
			case R.styleable.ThumbnailWrapper_ratio_width:
				this.ratioWidth = a.getFloat(attr, DEFAULT_RATIO_WIDTH);
				break;
			case R.styleable.ThumbnailWrapper_ratio_height:
				this.ratioHeight = a.getFloat(attr, DEFAULT_RATIO_HEIGHT);
				break;
			}
		}
		a.recycle();
		
		ratio = ratioHeight / ratioWidth;
	}
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int w = MeasureSpec.getSize(widthMeasureSpec);
		int h;
		
		if (maintain) {
			h = (int) (w * ratio);
		} else {
			h = MeasureSpec.getSize(heightMeasureSpec);
		}
    	this.measureChildren(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    	setMeasuredDimension(w, h);
	}

	public void setMaintainAspectRatio(boolean maintain) {
		this.maintain = maintain;
	}
	
	public int getDeclaredHeight() {
		return declaredHeight;
	}
	
}
