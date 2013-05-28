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

import com.concentricsky.android.khanacademy.data.remote.BaseEntityUpdateVisitor;
import com.concentricsky.android.khanacademy.data.remote.EntityVisitor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@JsonIgnoreProperties(ignoreUnknown=true)
@DatabaseTable(tableName = "video")
public class Video extends EntityBase {
	
	public static final byte DL_STATUS_NOT_STARTED = 0;
	public static final byte DL_STATUS_IN_PROGRESS = 1;
	public static final byte DL_STATUS_COMPLETE    = 2;
	
	
	/*
	 *   Full Video
	 *   
	 
	 // Always in both
	"kind": "Video",
    "title": "CA Algebra I: Number Properties and Absolute Value",
    
     // In full versions of both
    "description": "1-7, number properties and absolute value equations",
    "relative_url": "/video/ca-algebra-i--number-properties-and-absolute-value",
    "ka_url": "http://www.khanacademy.org/video/ca-algebra-i--number-properties-and-absolute-value",
    "backup_timestamp": "2012-10-16T16:23:04Z",
	
	 // Video Only
    "has_questions": false,
    "extra_properties": null,
    "node_slug": "v/ca-algebra-i--number-properties-and-absolute-value",
    "url": "http://www.youtube.com/watch?v=ZouQdHSyelg&feature=youtube_gdata_player",
    "progress_key": "v24364",
    "author_names": [
        "Sal Khan"
    ],
    "duration": 722,
    "keywords": "californa, standards, algebra, inequality, equations",
    "youtube_id": "ZouQdHSyelg",
    "download_urls": {
        "mp4": "http://s3.amazonaws.com/KA-youtube-converted/ZouQdHSyelg.mp4/ZouQdHSyelg.mp4",
        "png": "http://s3.amazonaws.com/KA-youtube-converted/ZouQdHSyelg.mp4/ZouQdHSyelg.png",
        "m3u8": "http://s3.amazonaws.com/KA-youtube-converted/ZouQdHSyelg.m3u8/ZouQdHSyelg.m3u8"
    },
    "date_added": "2011-02-20T16:34:07Z",
    "views": 120729,
    "readable_id": "ca-algebra-i--number-properties-and-absolute-value"

	 */
	
	/*
	 *   Video as child
	 *   
	 *   
            "kind": "Video",
            "hide": false,
            "title": "CA Algebra I: Number Properties and Absolute Value",
            "url": "http://www.khanacademy.org/video/ca-algebra-i--number-properties-and-absolute-value",
            "key_id": 24364,
            "id": "ca-algebra-i--number-properties-and-absolute-value"
	 */

	// columnName="_id" for CursorAdapter use.
	@DatabaseField(generatedId=true)
	int _id;
	
	@DatabaseField
	String readable_id;
	
	@DatabaseField
	int download_status;
	
	@DatabaseField
	String keywords;
	
	@DatabaseField
	String progress_key;
	
	@DatabaseField
	int duration;
	
	@DatabaseField(index=true)
	String youtube_id;
	
	public static class DownloadUrls {
		String mp4;
		String png;
		String m3u8;
		/**
		 * @return the mp4
		 */
		public String getMp4() {
			return mp4;
		}
		/**
		 * @param mp4 the mp4 to set
		 */
		public void setMp4(String mp4) {
			this.mp4 = mp4;
		}
		/**
		 * @return the png
		 */
		public String getPng() {
			return png;
		}
		/**
		 * @param png the png to set
		 */
		public void setPng(String png) {
			this.png = png;
		}
		/**
		 * @return the m3u8
		 */
		public String getM3u8() {
			return m3u8;
		}
		/**
		 * @param m3u8 the m3u8 to set
		 */
		public void setM3u8(String m3u8) {
			this.m3u8 = m3u8;
		}
		
		
	}
	
	DownloadUrls download_urls;
	
	@DatabaseField
	String mp4url;
	
	@DatabaseField
	String pngurl;
	
	@DatabaseField
	String m3u8url;
	
	// datetime
	@DatabaseField
	String date_added;
	
	@DatabaseField
	int views;
	
	@DatabaseField
	long dlm_id;
	
	/**
	 * @return the download_status
	 */
	public int getDownload_status() {
		return download_status;
	}

	/**
	 * @param download_status the download_status to set
	 */
	public void setDownload_status(int download_status) {
		this.download_status = download_status;
	}

	/**
	 * @return the keywords
	 */
	public String getKeywords() {
		return keywords;
	}

	/**
	 * @param keywords the keywords to set
	 */
	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	/**
	 * @return the progress_key
	 */
	public String getProgress_key() {
		return progress_key;
	}

	/**
	 * @param progress_key the progress_key to set
	 */
	public void setProgress_key(String progress_key) {
		this.progress_key = progress_key;
	}

	/**
	 * @return the duration
	 */
	public int getDuration() {
		return duration;
	}

	/**
	 * @param duration the duration to set
	 */
	public void setDuration(int duration) {
		this.duration = duration;
	}

	/**
	 * @return the youtube_id
	 */
	public String getYoutube_id() {
		return youtube_id;
	}

	/**
	 * @param youtube_id the youtube_id to set
	 */
	public void setYoutube_id(String youtube_id) {
		this.youtube_id = youtube_id;
	}

	/**
	 * @return the download_urls
	 */
	public DownloadUrls getDownload_urls() {
		if (download_urls == null) {
			download_urls = new DownloadUrls();
			download_urls.setM3u8(m3u8url);
			download_urls.setMp4(mp4url);
			download_urls.setPng(pngurl);
		}
		return download_urls;
	}

	/**
	 * @param download_urls the download_urls to set
	 */
	public void setDownload_urls(DownloadUrls download_urls) {
		this.download_urls = download_urls;
//		boolean n = download_urls == null;
//		this.setMp4url(n? null: download_urls.getMp4());
//		this.setM3u8url(n? null: download_urls.getM3u8());
//		this.setPngurl(n? null: download_urls.getPng());
	}

	/**
	 * @return the date_added
	 */
	public String getDate_added() {
		return date_added;
	}

	/**
	 * @param date_added the date_added to set
	 */
	public void setDate_added(String date_added) {
		this.date_added = date_added;
	}

	/**
	 * @return the views
	 */
	public int getViews() {
		return views;
	}

	/**
	 * @param views the views to set
	 */
	public void setViews(int views) {
		this.views = views;
	}

	public long getDlm_id() {
		return dlm_id;
	}

	public void setDlm_id(long dlm_id) {
		this.dlm_id = dlm_id;
	}

	/**
	 * @return the readable_id
	 */
	public String getReadable_id() {
		return readable_id;
	}

	/**
	 * @param readable_id the readable_id to set
	 */
	public void setReadable_id(String readable_id) {
		this.readable_id = readable_id;
	}

	/**
	 * @return the mp4url
	 */
	public String getMp4url() {
		return mp4url;
	}

	/**
	 * @return the pngurl
	 */
	public String getPngurl() {
		return pngurl;
	}

	/**
	 * @return the m3u8url
	 */
	public String getM3u8url() {
		return m3u8url;
	}

	/**
	 * @param mp4url the mp4url to set
	 */
	public void setMp4url(String mp4url) {
		this.mp4url = mp4url;
	}

	/**
	 * @param pngurl the pngurl to set
	 */
	public void setPngurl(String pngurl) {
		this.pngurl = pngurl;
	}

	/**
	 * @param m3u8url the m3u8url to set
	 */
	public void setM3u8url(String m3u8url) {
		this.m3u8url = m3u8url;
	}

	
	@Override
	public boolean equals(Object other) {
		try {
			return ((Video) other).getReadable_id().equals(getReadable_id());
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return (getClass().hashCode() + getReadable_id().hashCode()) % Integer.MAX_VALUE;
	}

	/**
	 * Be sure to refresh this Video from the database before calling this, as it relies
	 * on a current value of download_staus.
	 */
	@Override
	public int getDownloaded_video_count() {
		return getDownload_status() == DL_STATUS_COMPLETE ? 1 : 0;
	}

	@Override
	public int getVideo_count() {
		return 1;
	}
	
	@Override
	public String getId() {
		return getReadable_id();
	}
	
	
	@Override
	public BaseEntityUpdateVisitor<Video> buildUpdateVisitor() {
		return new BaseEntityUpdateVisitor<Video>(this) {
			@Override
			public void visit(Video toUpdate) {
				super.visit(toUpdate);
				
				String value = Video.this.getDate_added();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setDate_added(value);
				}
				
				value = Video.this.getKeywords();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setKeywords(value);
				}
				
				value = Video.this.getProgress_key();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setProgress_key(value);
				}
				
				value = Video.this.getYoutube_id();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setYoutube_id(value);
				}
				
				DownloadUrls urls = Video.this.getDownload_urls();
				if (!isDefaultValue(urls, DownloadUrls.class)) {
					toUpdate.setDownload_urls(urls);
				}
				
				int n = Video.this.getDuration();
				if (!isDefaultValue(n, Integer.class)) {
					toUpdate.setDuration(n);
				}
				
				n = Video.this.getViews();
				if (!isDefaultValue(n, Integer.class)) {
					toUpdate.setViews(n);
				}
				
			}
			
			@Override
			protected boolean isDefaultValue(Object value, Class<?> valueType) {
				if (DownloadUrls.class.equals(valueType)) {
					return null == value;
				}
				return super.isDefaultValue(value, valueType);
			}
		};
	}
	
	@Override
	public void accept(EntityVisitor visitor) {
		visitor.visit(this);
	}
}
