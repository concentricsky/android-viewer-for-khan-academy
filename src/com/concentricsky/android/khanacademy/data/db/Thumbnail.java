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
package com.concentricsky.android.khanacademy.data.db;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.concentricsky.android.khan.R;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Thumbnail {
	
	public static final byte QUALITY_LOW = 1; // 120x90
	public static final byte QUALITY_MEDIUM = 2; // 320x180
	public static final byte QUALITY_HIGH = 3; // 480x360
	public static final byte QUALITY_SD = 4; // 640x480
	
	public static final byte AVAILABILITY_UNAVAILABLE = -1;
	public static final byte AVAILABILITY_UNKNOWN = 0;
	public static final byte AVAILABILITY_AVAILABLE = 1;
	
	private static final String HASH_FORMAT = "%d000:%s";
	
	@DatabaseField(generatedId=true)
	int _id;
	
	@DatabaseField(index=true)
	String youtube_id;

	@DatabaseField(index=true, dataType=DataType.BYTE)
	byte q; // quality
	
	@DatabaseField(dataType=DataType.BYTE)
	byte availability;
	
	@DatabaseField(dataType=DataType.BYTE_ARRAY)
	byte[] data;
	
	public Thumbnail() { }
	public Thumbnail(String youtubeId, byte quality) {
		setYoutube_id(youtubeId);
		setQ(quality);
	}
	public Thumbnail(String youtubeId, byte quality, Bitmap data) {
		this(youtubeId, quality);
		setBitmapData(data);
	}
	
	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getYoutube_id() {
		return youtube_id;
	}
	public void setYoutube_id(String youtube_id) {
		this.youtube_id = youtube_id;
	}
	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
	
	public byte getQ() {
		return q;
	}
	public void setQ(byte q) {
		this.q = q;
	}
	
	public byte getAvailability() {
		return availability;
	}
	public void setAvailability(byte availability) {
		this.availability = availability;
	}
	/**
	 * Get the bitmap represented by this Thumbnail.
	 * 
	 * NOTE: This can be relatively expensive, and should probably be called from an AsyncTask.
	 * 
	 * @return The bitmap or null.
	 */
	public Bitmap asBitmap() {
		byte[] data = getData();
		if (data == null) {
			return null;
		}
		return BitmapFactory.decodeByteArray(data, 0, data.length);
	}
	
	public void setBitmapData(Bitmap data) {
		if (data == null) {
			setData(null);
			return;
		}
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		data.compress(Bitmap.CompressFormat.PNG, 100, stream);
		byte[] byteArray = stream.toByteArray();
		setData(byteArray);
	}
	
	public static String getDownloadUrl(Context context, byte quality, String youtubeId) {
		int urlFormatId;
		switch (quality) {
		// R.string.url_format_thumbnail_sd is also available, and higher quality yet than hq.
		case QUALITY_SD:
			urlFormatId = R.string.url_format_thumbnail_sd;
			break;
		case QUALITY_HIGH:
			urlFormatId = R.string.url_format_thumbnail_hq;
			break;
		case QUALITY_MEDIUM:
			urlFormatId = R.string.url_format_thumbnail_mq;
			break;
		case QUALITY_LOW:
		default:
			urlFormatId = R.string.url_format_thumbnail_lq;
			break;
		}
		String urlFormat = context.getString(urlFormatId);
		return String.format(urlFormat, youtubeId, Locale.US);
	}
	
	public String getDownloadUrl(Context context) {
		return getDownloadUrl(context, getQ(), getYoutube_id());
	}
	
	@Override
	public int hashCode() {
		// This trusts in the documented algorithm used in String#hashCode:
		// s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
		
		// As these youtube ids are only 11 characters long, we have some room to play.
		// Pretend there is a single character representing the quality a few places before the beginning of the string.
		String yid = getYoutube_id();
		if (yid == null) {
			yid = "";
		}
		int q = getQ();
		return String.format(HASH_FORMAT, q, yid).hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		return (o instanceof Thumbnail) && o.hashCode() == hashCode();
	}
	
}
