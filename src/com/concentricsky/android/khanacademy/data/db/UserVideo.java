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

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@JsonIgnoreProperties(ignoreUnknown=true)
@DatabaseTable(tableName = "uservideo")
public class UserVideo extends ModelBase {
	
	@DatabaseField(generatedId=true)
	int id;
	
	@DatabaseField(foreign=true)
	User user;
	
	@DatabaseField
	String video_id;
//	@DatabaseField(foreign=true)
//	Video video;
	
	@DatabaseField
	boolean completed;
	
	@DatabaseField
	int duration;
	
	@DatabaseField
	int last_second_watched;
	
	@DatabaseField
	Date last_watched;
	
	@DatabaseField
	int points;
	
	@DatabaseField
	int seconds_watched;

	/**
	 * @return the user
	 */
	public User getUser() {
		return user;
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(User user) {
		this.user = user;
	}

	public String getVideo_id() {
		return video_id;
	}
	public void setVideo_id(String video_id) {
		this.video_id = video_id;
	}
	
	/**
	 * @return the completed
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * @param completed the completed to set
	 */
	public void setCompleted(boolean completed) {
		this.completed = completed;
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
	 * @return the last_second_watched
	 */
	public int getLast_second_watched() {
		return last_second_watched;
	}

	/**
	 * @param last_second_watched the last_second_watched to set
	 */
	public void setLast_second_watched(int last_second_watched) {
		this.last_second_watched = last_second_watched;
	}

	/**
	 * @return the last_watched
	 */
	public Date getLast_watched() {
		return last_watched;
	}

	/**
	 * @param last_watched the last_watched to set
	 */
	public void setLast_watched(Date last_watched) {
		this.last_watched = last_watched;
	}

	/**
	 * @return the points
	 */
	public int getPoints() {
		return points;
	}

	/**
	 * @param points the points to set
	 */
	public void setPoints(int points) {
		this.points = points;
	}

	/**
	 * @return the seconds_watched
	 */
	public int getSeconds_watched() {
		return seconds_watched;
	}

	/**
	 * @param seconds_watched the seconds_watched to set
	 */
	public void setSeconds_watched(int seconds_watched) {
		this.seconds_watched = seconds_watched;
	}

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}
	
	/** For Jackson. API responses include full Video objects on the key "video". */
	public void setVideo(Video video) {
		this.video_id = video.getReadable_id();
	}
	
	/*
[
   {
        "completed": false, 
        "duration": 172, 
        "kind": "UserVideo", 
        "last_second_watched": 20, 
        "last_watched": "2011-05-04T06:01:47Z", 
        "points": 44, 
        "seconds_watched": 10, 
        "user": "you@gmail.com",
        "video": {
            "date_added": "2011-03-04T06:01:47Z", 
            "description": "U03_L2_T2_we1 Multiplying Decimals", 
            "duration": 172, 
            "ka_url": "http://www.khanacademy.org/video/multiplying-decimals", 
            "keywords": "U03_L2_T2_we1, Multiplying, Decimals", 
            "kind": "Video", 
            "playlists": [
                "Developmental Math"
            ], 
            "readable_id": "multiplying-decimals", 
            "title": "Multiplying Decimals", 
            "url": "http://www.youtube.com/watch?v=JEHejQphIYc&feature=youtube_gdata_player", 
            "views": 9837, 
            "youtube_id": "JEHejQphIYc"
        }
    },
    ...
]

	 */
}
