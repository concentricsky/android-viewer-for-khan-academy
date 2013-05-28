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
package com.concentricsky.android.khanacademy.data.remote;

import static com.concentricsky.android.khanacademy.Constants.RESULT_CODE_FAILURE;
import static com.concentricsky.android.khanacademy.Constants.RESULT_CODE_SUCCESS;
import static com.concentricsky.android.khanacademy.Constants.SETTINGS_NAME;
import static com.concentricsky.android.khanacademy.Constants.SETTING_LIBRARY_ETAG;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpStatus;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.WifiReceiver;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.DatabaseHelper;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.util.Log;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;

public class LibraryUpdaterTask extends AsyncTask<Void, Void, Integer> {
	
	public static final String LOG_TAG = LibraryUpdaterTask.class.getSimpleName();
	
	private final OrmLiteSqliteOpenHelper tempDbHelper;
	private final String topicTableName = "topic";
	private final String videoTableName = "video";
	private final String topicvideoTableName = "topicvideo";
	private final KADataService dataService;
	private final ConnectivityManager connectivityManager;
	
	private final String url = "http://www.khanacademy.org/api/v1/topictree";
	public boolean force = false;
	
	private static final List<String> downloadUrlFields = Arrays.asList(new String[] {"png", "mp4", "m3u8"});
	private static final List<String> stringFields = Arrays.asList(new String[] {"kind", "title", "description", "ka_url", "id", "readable_id", "youtube_id", "keywords", "progress_key", "date_added"}); // 10
	private static final List<String> booleanFields = Arrays.asList(new String[] {"hide"});
	private static final List<String> intFields =  Arrays.asList(new String[] {"views", "duration"});
	// "children", "download_urls" are special

	/** The return values we need when parsing an array of children. */
	private class ChildArrayResults {
		List<String> childIds = new ArrayList<String>();
		String childKind;
		int videoCount;
		String thumbId;
	}
	
	public LibraryUpdaterTask(KADataService dataService) {
		this.dataService = dataService;
		this.tempDbHelper = new TempHelper(dataService, dataService.getHelper().getReadableDatabase());
		connectivityManager = (ConnectivityManager) dataService.getSystemService(Context.CONNECTIVITY_SERVICE);
	}
	
	@Override
	protected Integer doInBackground(Void... params) {
		
		SharedPreferences prefs = dataService.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
		
		// Connectivity receiver.
		final NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
		ComponentName receiver = new ComponentName(dataService, WifiReceiver.class);
		PackageManager pm = dataService.getPackageManager();
		if (activeNetwork == null || !activeNetwork.isConnected()) {
			// We've missed a scheduled update. Enable the receiver so it can launch an update when we reconnect.
			Log.d(LOG_TAG, "Missed library update: not connected. Enabling connectivity receiver.");
			pm.setComponentEnabledSetting(receiver,
			        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
			        PackageManager.DONT_KILL_APP);
			return RESULT_CODE_FAILURE;
		} else {
			// We are connected. Disable the receiver.
			Log.d(LOG_TAG, "Library updater connected. Disabling connectivity receiver.");
			pm.setComponentEnabledSetting(receiver,
			        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
			        PackageManager.DONT_KILL_APP);
		}
		
		InputStream in = null;
		String etag = prefs.getString(SETTING_LIBRARY_ETAG, null);
		
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			if (etag != null && !force) {
				conn.setRequestProperty("If-None-Match", etag);
			}
			
			int code = conn.getResponseCode();
			switch (code) {
			case HttpStatus.SC_NOT_MODIFIED:
				// If we got a 304, we're done.
				// Use failure code to indicate there is no temp db to copy over.
				Log.d(LOG_TAG, "304 in library response.");
				return RESULT_CODE_FAILURE;
			default:
				// Odd, but on 1/3/13 I received correct json responses with a -1 for responseCode. Fall through.
				Log.w(LOG_TAG, "Error code in library response: " + code);
			case HttpStatus.SC_OK:
				// Parse response.
				in = conn.getInputStream();
				JsonFactory factory = new JsonFactory();
				final JsonParser parser = factory.createJsonParser(in);
				
				SQLiteDatabase tempDb = tempDbHelper.getWritableDatabase();
				tempDb.beginTransaction();
				try {
					tempDb.execSQL("delete from topic");
					tempDb.execSQL("delete from topicvideo");
					tempDb.execSQL("delete from video");
					
					parseObject(parser, tempDb, null, 0);
					tempDb.setTransactionSuccessful();
				} catch (Exception e) {
					e.printStackTrace();
					return RESULT_CODE_FAILURE;
				} finally {
					tempDb.endTransaction();
					tempDb.close();
				}
				
				// Save etag once we've successfully parsed the response.
				etag = conn.getHeaderField("ETag");
				prefs.edit().putString(SETTING_LIBRARY_ETAG, etag).apply();
				
				// Move this new content from the temp db into the main one.
				mergeDbs();
				
				return RESULT_CODE_SUCCESS;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			tempDbHelper.close();
		}
		
		return RESULT_CODE_FAILURE;
	}

	private ContentValues parseObject(JsonParser parser, SQLiteDatabase tempDb, String parentId, int seq) throws JsonParseException, IOException {
		// TODO : Grab id of root topic here, and store it in shared prefs, in case it ever
		//        changes. Currently we assume "root" and a change would be catastrophic.
		ContentValues result = new ContentValues();
		ChildArrayResults childResults = null;
		boolean badKind = false;
		
		result.put("parentTopic_id", parentId);
		result.put("seq", seq);
		
		while (parser.nextValue() != JsonToken.END_OBJECT) {
			
			// Allows us to burn through the rest of the object once we discover it's an exercise or something else we don't care about.
			if (badKind) continue;
			
			String fieldName = parser.getCurrentName();
			
			// Keys present will determine object type.
			if (stringFields.contains(fieldName)) {
				// Use getValueAsString over getText; getText returns "null" while getValueAsString returns null.
				String value = parser.getValueAsString();
				result.put(fieldName, value);
				
				if ("id".equals(fieldName)) {
					if (childResults != null) {
						addParentIdToChildren(tempDb, childResults, value);
					}
				}
			} else if (intFields.contains(fieldName)) {
				result.put(fieldName, parser.getIntValue());
			} else if (booleanFields.contains(fieldName)) {
				result.put(fieldName, parser.getBooleanValue());
			} else if ("children".equals(fieldName)) {
				childResults = parseChildArray(parser, tempDb, result.containsKey("id") ? result.getAsString("id") : null);
				result.put("video_count", childResults.videoCount);
				result.put("child_kind", childResults.childKind);
				result.put("thumb_id", childResults.thumbId);
			} else if ("download_urls".equals(fieldName)) {
				parseDownloadUrls(parser, result);
			} else if (null == fieldName) {
				// Noop. Just in case.
			} else {
				JsonToken next = parser.getCurrentToken();
				if (next == JsonToken.START_OBJECT || next == JsonToken.START_ARRAY) {
					// Skip this object or array, leaving us pointing at the matching end_object / end_array token.
					parser.skipChildren();
				}
			}
		}
		
		// Ignore types we don't need.
		if (badKind) {
			return null;
		}
		
		// Having parsed this whole object, we can insert it.
		if (result.containsKey("kind")) {
			String kind = result.getAsString("kind");
			if ("Topic".equals(kind)) {
				if (result.containsKey("id")) {
					result.put("_id", result.getAsString("id"));
					result.remove("id");
				}
				if (result.containsKey("child_kind")) {
					String child_kind = result.getAsString("child_kind");
					if ("Topic".equals(child_kind) || "Video".equals(child_kind)) {
						insertTopic(tempDb, result);
					}
				}
			} else if ("Video".equals(kind)) {
				if (result.containsKey("id")) {
					result.put("video_id", result.getAsString("id"));
					result.remove("id");
				}
				insertTopicVideo(tempDb, result);
				insertVideo(tempDb, result);
			}
		}
		
		return result;
	}
	
	// return list of ids of the children in this array, so the parent can attach a parentTopic_id after the fact.
	private ChildArrayResults parseChildArray(JsonParser parser, SQLiteDatabase tempDb, String parentTopic_id) throws JsonParseException, IOException {
		ChildArrayResults result = new ChildArrayResults();
		int seq = 0;
		
		JsonToken currentToken = parser.getCurrentToken();
		if (currentToken == JsonToken.START_ARRAY) {
			while (parser.nextValue() == JsonToken.START_OBJECT) { // Otherwise, we will be at END_ARRAY here.
				ContentValues values = parseObject(parser, tempDb, parentTopic_id, seq++);
				
				if (values != null && values.containsKey("kind")) {
					String kind = values.getAsString("kind");
					
					if ("Topic".equals(kind) && values.containsKey("_id")) {
						result.childKind = kind;
						result.childIds.add(values.getAsString("_id"));
						result.videoCount += values.getAsInteger("video_count");
						if (result.thumbId == null && values.containsKey("thumb_id")) {
							// Return the first available thumb id as this topic's thumb id.
							result.thumbId = values.getAsString("thumb_id");
						}
					} else if ("Video".equals(kind) && values.containsKey("readable_id")) {
						result.childKind = kind;
						result.childIds.add(values.getAsString("readable_id"));
						result.videoCount += 1;
						if (result.thumbId == null && values.containsKey("pngurl")) {
							// Return youtube_id of first video with a thumbnail as this topic's thumbnail id.
							result.thumbId = values.getAsString("youtube_id");
						}
					}
				}
			}
		}
		return result;
	}
	
	private void parseDownloadUrls(JsonParser parser, ContentValues result) throws JsonParseException, IOException {
		// parser points at begin object token right now
		if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
			while (parser.nextValue() != JsonToken.END_OBJECT) {
				String fieldName = parser.getCurrentName();
				
				if (downloadUrlFields.contains(fieldName)) {
					result.put(fieldName + "url", parser.getValueAsString());
				}
			}
		}
//		else we must not have any download urls (null, '', something like that.)
	}
	
	// When we encounter our id, if we have already parsed children, then we must select all of 
	// the correct type, with the ids we received from the children, who don't have parentTopic_ids, and set them.
	// It appears, looking at the tree as returned 12/30/12, that this is unnecessary. It's here just in case.
	private void addParentIdToChildren(SQLiteDatabase tempDb, ChildArrayResults childResults, String parentId) {
		
		if (childResults == null || childResults.childIds == null || childResults.childIds.size() == 0) {
			return;
		}
		
		ContentValues values = new ContentValues();
		values.put("parentTopic_id", parentId);
		
		boolean isTopic = "Topic".equals(childResults.childKind);
		String table = isTopic ? topicTableName : videoTableName;
		
		StringBuilder where = new StringBuilder();
		where.append(isTopic ? "_id IN (?" : "readable_id IN (?");
		// First '?' already added, so start at 1.
		for (int i = 1; i < childResults.childIds.size(); ++i) {
			where.append(",?");
		}
		where.append(")");
		String whereClause = where.toString();
		
		String[] whereArg = childResults.childIds.toArray(new String[childResults.childIds.size()]);
		
		tempDb.update(table, values, whereClause, whereArg);
	}
	
	private void insertTopic(SQLiteDatabase tempDb, ContentValues values) {
		tempDb.insert(topicTableName, null, values);
	}
	
	private void insertTopicVideo(SQLiteDatabase tempDb, ContentValues values) {
		// This values is for a video.
		ContentValues v = new ContentValues();
		v.put("topic_id", values.getAsString("parentTopic_id"));
		v.put("video_id", values.getAsString("readable_id"));
		tempDb.insertWithOnConflict(topicvideoTableName, null, v, SQLiteDatabase.CONFLICT_IGNORE);
	}
	
	private void insertVideo(SQLiteDatabase tempDb, ContentValues values) {
		tempDb.insertWithOnConflict(videoTableName, null, values, SQLiteDatabase.CONFLICT_IGNORE);
	}
	
	private void mergeDbs() {
		Log.d(LOG_TAG, "update received - juggling dbs");
		// Get main database, attach temp db to it.
		SQLiteDatabase mainDb = dataService.getHelper().getWritableDatabase();
		mainDb.execSQL("attach database ? as ka_temp", new Object[] {dataService.getDatabasePath("ka_temp").getAbsolutePath()});
		
		mainDb.beginTransaction();
		try {
			
			// Maintain download status.
			String sql = "select max(download_status), dlm_id, youtube_id from video where download_status != ? group by youtube_id";
			Cursor c = mainDb.rawQuery(sql, new String[] {"" + Video.DL_STATUS_NOT_STARTED});
			Cursor c1;
			String[] videoIds = new String[c.getCount()];
			int i = 0;
			while (c.moveToNext()) {
				String youtube_id = c.getString(c.getColumnIndex("youtube_id"));
				String download_status = c.getString(c.getColumnIndex("max(download_status)"));
				long dlm_id = c.getLong(c.getColumnIndex("dlm_id"));
				videoIds[i++] = youtube_id;
				ContentValues v = new ContentValues();
				v.put("download_status", download_status);
				v.put("dlm_id", dlm_id);
				String[] idArg = new String[] {youtube_id};
				mainDb.update("ka_temp.video", v, "youtube_id = ?", idArg);
				
				// cursor over parent topics of this video
				sql = "select ka_temp.topic._id from ka_temp.topic, ka_temp.topicvideo, ka_temp.video where ka_temp.video.youtube_id=? and ka_temp.topicvideo.video_id=ka_temp.video.readable_id and ka_temp.topicvideo.topic_id=ka_temp.topic._id";
				c1 = mainDb.rawQuery(sql, idArg);
				Log.d(LOG_TAG, String.format("updating counts for %d topics", c1.getCount()));
				while (c1.moveToNext()) {
					String topicId = c1.getString(c1.getColumnIndex("_id"));
					DatabaseHelper.incrementDownloadedVideoCounts(mainDb, topicId, "ka_temp.topic");
				}
				c1.close();
			}
			c.close();
			
			mainDb.execSQL("delete from topic");
			mainDb.execSQL("insert into topic select * from ka_temp.topic");
			
			mainDb.execSQL("delete from topicvideo");
			mainDb.execSQL("insert into topicvideo select * from ka_temp.topicvideo");
		
			mainDb.execSQL("delete from video");
			mainDb.execSQL("insert into video select * from ka_temp.video");
			
			mainDb.setTransactionSuccessful();
		} finally {
			mainDb.endTransaction();
			mainDb.execSQL("detach database ka_temp");
		}
		
		Log.d(LOG_TAG, "finished juggling");
	}
	
	private class TempHelper extends OrmLiteSqliteOpenHelper {
		
		static final String DB_NAME = "ka_temp";
		final List<String> schema = new ArrayList<String>();
		
		TempHelper(Context context, SQLiteDatabase mainDb) {
			super(context, DB_NAME, null, 1, R.raw.ormlite_config);
			
			context.deleteDatabase(DB_NAME);
			
			Cursor cursor = mainDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"topic"});
			while (cursor.moveToNext()) {
				String sql = cursor.getString(0);
				Log.d(LOG_TAG, "TempHelper: " + sql);
				if (sql != null) {
					schema.add(sql);
				}
			}
			cursor.close();
			
			cursor = mainDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"video"});
			while (cursor.moveToNext()) {
				String sql = cursor.getString(0);
				Log.d(LOG_TAG, "TempHelper: " + sql);
				if (sql != null) {
					schema.add(sql);
				}
			}
			cursor.close();
			
			cursor = mainDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"topicvideo"});
			while (cursor.moveToNext()) {
				String sql = cursor.getString(0);
				Log.d(LOG_TAG, "TempHelper: " + sql);
				if (sql != null) {
					schema.add(sql);
				}
			}
			cursor.close();
		}

		@Override
		public void onCreate(SQLiteDatabase database,
				ConnectionSource connectionSource) {
			
			Log.d(LOG_TAG, "TempHelper.onCreate");
			for (String sql : schema) {
				Log.d(LOG_TAG, sql);
				database.execSQL(sql);
			}
			
		}

		@Override
		public void onUpgrade(SQLiteDatabase database,
				ConnectionSource connectionSource, int oldVersion,
				int newVersion) {
		}
	}
	
}
