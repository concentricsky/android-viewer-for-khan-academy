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
package com.concentricsky.android.khanacademy;

public interface Constants {
	
	public static final String ACTION_TOAST = "ACTION_TOAST";
	public static final String ACTION_BADGE_EARNED = "ACTION_BADGE_EARNED";
	public static final String ACTION_DOWNLOAD_PROGRESS_UPDATE = "ACTION_DOWNLOAD_PROGRESS_UPDATE";
	public static final String ACTION_LIBRARY_UPDATE = "ACTION_LIBRARY_UPDATE";
	public static final String ACTION_UPDATE_DOWNLOAD_STATUS = "ACTION_UPDATE_DOWNLOAD_STATUS";
	
	public static final String EXTRA_BADGE = "EXTRA_BADGE";
	public static final String EXTRA_FORCE = "EXTRA_FORCE";
	public static final String EXTRA_ID = "EXTRA_ID";
	public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";
	public static final String EXTRA_STATUS = "EXTRA_STATUS";
	public static final String EXTRA_YOUTUBE_ID = "EXTRA_YOUTUBE_ID";
	
	public static final String SETTINGS_NAME = "com.concentricsky.android.khanacademy.SETTINGS";
	public static final String SETTING_ACKNOWLEDGEMENT = "SETTING_ACKNOWLEDGEMENT";
	public static final String SETTING_LIBRARY_ETAG = "SETTING_LIBRARY_ETAG";
	public static final String SETTING_OAUTH_TOKEN = "SETTING_OAUTH_TOKEN";
	public static final String SETTING_OAUTH_SECRET = "SETTING_OAUTH_SECRET";
	public static final String SETTING_SHOW_DL_ONLY = "SETTING_SHOW_DL_ONLY";
	public static final String SETTING_USERID = "SETTING_USERID";
	public static final String SETTING_VIDEOID = "SETTING_VIDEOID";
	
	public static final String PARAM_ARGS = "PARAM_ARGS";
	public static final String PARAM_OAUTH_SECRET = "PARAM_OAUTH_SECRET";
	public static final String PARAM_OAUTH_TOKEN = "PARAM_OAUTH_TOKEN";
	public static final String PARAM_USERID = "PARAM_USERID";
	public static final String PARAM_TASK_ID = "PARAM_TASK_ID";
	public static final String PARAM_TOPIC_ID = "PARAM_TOPIC_ID";
	public static final String PARAM_TOPIC_IDS = "PARAM_TOPIC_IDS";
	public static final String PARAM_SHOW_DL_ONLY = "PARAM_SHOW_DL_ONLY";
	public static final String PARAM_USERVIDEO_POINTS = "PARAM_USERVIDEO_POINTS";
	public static final String PARAM_VIDEO_ID = "PARAM_VIDEO_ID";
	public static final String PARAM_VIDEO_IDS = "PARAM_VIDEO_IDS";
	public static final String PARAM_VIDEO_FULLSCREEN = "PARAM_VIDEO_FULLSCREEN";
	public static final String PARAM_VIDEO_PLAY_STATE = "PARAM_VIDEO_PLAY_STATE";
	public static final String PARAM_VIDEO_POSITION = "PARAM_VIDEO_POSITION";
	public static final int PARAM_PROGRESS_DONE = 100;
	public static final int PARAM_PROGRESS_UNKNOWN = -12345;
	
	public static final String TAG_ROOT = "TAG_ROOT";
	public static final String TAG_LIST_FRAGMENT = "TAG_LIST_FRAGMENT";
	public static final String TAG_DETAIL_FRAGMENT = "TAG_DETAIL_FRAGMENT";
	public static final String TAG_VIDEO_FRAGMENT = "TAG_VIDEO_FRAGMENT";
	public static final String TAG_FS_FRAGMENT = "TAG_FS_FRAGMENT";
	public static final String TAG_CAPTION_FRAGMENT = "TAG_CAPTION_FRAGMENT";
	
	public static final int REQUEST_CODE_FS_VIDEO = 2389;
	public static final int REQUEST_CODE_USER_LOGIN = 98347;
	public static final int REQUEST_CODE_RECURRING_LIBRARY_UPDATE = 23984;
	public static final int REQUEST_CODE_ONE_TIME_LIBRARY_UPDATE = 23985;
	public static final int RESULT_CODE_SUCCESS = 345;
	public static final int RESULT_CODE_FAILURE = 9213854;
	
	public static final int UPDATE_DELAY_FROM_FIRST_RUN = 60 * 60 * 3;
	public static final int UPDATE_DELAY_FROM_NETWORK_CONNECT = 4;
	
    public static final int POSITION_UPDATE_DELAY = 333;
    public static final float LOG_INTERVAL_PERCENT = 0.05f;
    public static final int LOG_INTERVAL_SECONDS = 10;
    public static final int MAX_INTERVAL_WITHOUT_SEEK = 1;
	
	public static final String OAUTH_ACCESS_TOKEN_URL = "http://www.khanacademy.org/api/auth/access_token";
	public static final String OAUTH_AUTHORIZE_URL = "";
	public static final String OAUTH_REQUEST_TOKEN_URL = "http://www.khanacademy.org/api/auth/request_token";

	public static final String COL_VIDEO_ID = "youtube_id";
	public static final String COL_TOPIC_ID = "_id";
	public static final String COL_FK_TOPIC = "parentTopic_id";
	public static final String COL_DL_STATUS = "download_status";
	public static final String TABLE_VIDEO  = "video";
	public static final String TABLE_TOPIC  = "topic";
	
	public static final String DEFAULT_VIDEO_ID = "salman-khan-talk-at-ted-2011--from-ted-com";
	
	public enum Direction {
		FORWARD, BACKWARD
	}

	public static final String ACTION_OFFLINE_VIDEO_SET_CHANGED = "ACTION_OFFLINE_VIDEO_SET_CHANGED";
}
