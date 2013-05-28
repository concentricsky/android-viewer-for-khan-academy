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

import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_PAUSED;
import static android.app.DownloadManager.STATUS_RUNNING;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;
import static com.concentricsky.android.khanacademy.Constants.COL_DL_STATUS;
import static com.concentricsky.android.khanacademy.Constants.COL_FK_TOPIC;
import static com.concentricsky.android.khanacademy.Constants.COL_TOPIC_ID;
import static com.concentricsky.android.khanacademy.Constants.TABLE_TOPIC;
import static com.concentricsky.android.khanacademy.Constants.TABLE_VIDEO;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.util.DatabaseImporter;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.OfflineVideoManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
	public static final String LOG_TAG = "DatabaseHelper";
	
	public static final String DATABASE_NAME = "ka.sqlite3";
	public static final int DATABASE_RESOURCE_ID = R.raw.db;
	public static final int DATABASE_VERSION = 120; // analagous to 1.2.0 release
	
	private Context context;
	private Dao<Video, String> videoDao;
	private Dao<Topic, String> topicDao;
	private Dao<User, String> userDao;
	private Dao<UserVideo, Integer> userVideoDao;
	private StringBuilder stringBuilder = new StringBuilder();
	private Formatter stringFormatter = new Formatter(stringBuilder);
	
	public static enum Type {
		USER(User.class),
		USERVIDEO(UserVideo.class),
		VIDEO(Video.class),
		TOPIC(Topic.class);
		
		public final Class<?> cls;
		
		Type(Class<?> cls) {
			this.cls = cls;
		}
	}
	
	public interface Callbacks {
		public abstract void onChange();
	}
	
	private ArrayList<Callbacks> callbacks = new ArrayList<Callbacks>();
	
	public void registerCallbacks(Callbacks callbacks) {
		this.callbacks.add(callbacks);
	}
	
	public boolean unregisterCallbacks(Callbacks callbacks) {
		return this.callbacks.remove(callbacks);
	}

	public DatabaseHelper(Context context) {
	    super(context, DATABASE_NAME, null, DATABASE_VERSION,
	        R.raw.ormlite_config);
		this.context = context;
	}
	
	private boolean databaseExists() {
		for (String name : context.databaseList()) {
			if (name.equals(getDatabaseName())) {
				return true;
			}
		}
		return false;
	}
	
	private void copyRawDatabase() {
		new DatabaseImporter(context).import_(DATABASE_RESOURCE_ID, getDatabaseName());
	}
	
	@Override
	public SQLiteDatabase getWritableDatabase() {
		if (!databaseExists()) {
			copyRawDatabase();
		}
		return super.getWritableDatabase();
	}

	@Override
	public SQLiteDatabase getReadableDatabase() {
		if (!databaseExists()) {
			copyRawDatabase();
		}
		return super.getReadableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		
	}
	

	@Override
	public void onUpgrade(final SQLiteDatabase database,
			final ConnectionSource connectionSource, final int oldVersion, final int newVersion) {
		
		
		if (oldVersion < 111) {
			// Create new Caption table.
			try {
				TableUtils.createTableIfNotExists(connectionSource, Caption.class);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		if (oldVersion < 114) {
			// Could instead create an ORMLite pojo for topicvideo and do this with TableUtils.
			database.execSQL("CREATE TABLE IF NOT EXISTS `topicvideo` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `topic_id` VARCHAR, `video_id` VARCHAR )");
		}
		
		if (newVersion >= 111) {
			
			// Played with the idea of putting this all into an AsyncTask, but
			//  1) Can crash the app. For example, in 114 upgrade we add topicvideo table; if it doesn't exist and user reaches a video list, we 
			//     crash. Worse, if we do crash here the app is broken. Db version has been bumped, so this code doesn't get another chance to run.
			//  2) To prevent crash, tried showing AlertDialog. The context we get here is "not an application context", so trying to show a window
			//     (or sometimes a toast) crashes us with a null window token error. I dug deep to try to pass in a useful context with no luck.
			//     See branch temp/pass_context_to_helper.
			//  3) It doesn't take very long (truncate / reload full video and topic tables is just a few seconds, not long enough for ANR on Fire HD)
			
			new DatabaseImporter(context).import_(DATABASE_RESOURCE_ID, "temp");
			SQLiteDatabase tempDb = new TempHelper(context).getReadableDatabase();
			
			if (oldVersion < 111) {
				do111Upgrade(database, tempDb);
			}
			
			if (oldVersion < 114 && newVersion >= 114) {
				// add topicvideo table
				do114Upgrade(database, tempDb);
			}
			
			if (oldVersion < 120 && newVersion >= 120) {
				// add video.dlm_id column
				do120Upgrade(database, tempDb);
			}
			
			tempDb.close();
			context.deleteDatabase(TempHelper.DB_NAME);
			
			// Update download status from storage. This recovers any lost during library updates in 1.1.3.
			database.beginTransaction();
			try {
				for (String yid : getAllDownloadedYoutubeIds()) {
					database.execSQL("update video set download_status=? where youtube_id=?", new Object[] {Video.DL_STATUS_COMPLETE, yid});
				}
				database.setTransactionSuccessful();
			} finally {
				database.endTransaction();
			}
					
		}
			
	}

	private void do111Upgrade(SQLiteDatabase database, SQLiteDatabase tempDb) {
		Log.d(LOG_TAG, "do111Upgrade");
		
		Cursor c;
			
		// Maintain download status.
		tempDb.beginTransaction();
		try {
			String sql = "select max(download_status), youtube_id from video where download_status != ? group by youtube_id";
			c = database.rawQuery(sql, new String[] {"" + Video.DL_STATUS_NOT_STARTED});
			while (c.moveToNext()) {
				String youtube_id = c.getString(c.getColumnIndex("youtube_id"));
				String download_status = c.getString(c.getColumnIndex("max(download_status)"));
				ContentValues v = new ContentValues();
				v.put("download_status", download_status);
				String[] idArg = new String[] {youtube_id};
				tempDb.update("video", v, "youtube_id = ?", idArg);
				
				// cursor over parent topics of this video
				sql = "select topic._id from topic, topicvideo, video where video.youtube_id=? and topicvideo.video_id=video.readable_id and topicvideo.topic_id=topic._id";
				Cursor c1 = tempDb.rawQuery(sql, idArg);
				Log.d(LOG_TAG, String.format("updating counts for %d topics", c1.getCount()));
				while (c1.moveToNext()) {
					String topicId = c1.getString(c1.getColumnIndex("_id"));
					incrementDownloadedVideoCounts(tempDb, topicId, "topic");
				}
				c1.close();
			}
			c.close();
			tempDb.setTransactionSuccessful();
		} finally {
			tempDb.endTransaction();
		}
			
		// Migrate topics.
		database.beginTransaction();
		try {
			List<String> schema = new ArrayList<String>();
			c = tempDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"topic"});
			while (c.moveToNext()) {
				String s = c.getString(0);
				if (s != null) {
					schema.add(s);
				}
			}
			c.close();
			database.execSQL("drop table topic");
			for (String s : schema) {
				Log.d(LOG_TAG, s);
				database.execSQL(s);
			}
			
			c = tempDb.rawQuery("select * from topic", null);
			while (c.moveToNext()) {
				ContentValues v = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, v);
				database.insert("topic", null, v);
			}
			c.close();
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		// Migrate videos.
		database.beginTransaction();
		try {
			List<String> schema = new ArrayList<String>();
			c = tempDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"video"});
			while (c.moveToNext()) {
				String s = c.getString(0);
				if (s != null) {
					schema.add(s);
				}
			}
			c.close();
			database.execSQL("drop table video");
			for (String s : schema) {
				Log.d(LOG_TAG, s);
				database.execSQL(s);
			}
			
			c = tempDb.rawQuery("select * from video", null);
			while (c.moveToNext()) {
				ContentValues v = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, v);
				database.insert("video", null, v);
			}
			c.close();
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		
		Log.d(LOG_TAG, "do111Upgrade returning");
	}
	
	private void do114Upgrade(SQLiteDatabase database, SQLiteDatabase tempDb) {
		Log.d(LOG_TAG, "do114Upgrade");
		
		database.beginTransaction();
		Cursor c = null;
		try {
			List<String> schema = new ArrayList<String>();
			c = tempDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"topicvideo"});
			while (c.moveToNext()) {
				String s = c.getString(0);
				if (s != null) {
					schema.add(s);
				}
			}
			c.close();
			database.execSQL("drop table topicvideo");
			for (String s : schema) {
				Log.d(LOG_TAG, s);
				database.execSQL(s);
			}
			
			c = tempDb.rawQuery("select * from topicvideo", null);
			while (c.moveToNext()) {
				ContentValues v = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, v);
				database.insert("topicvideo", null, v);
			}
			c.close();
			database.setTransactionSuccessful();
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
			database.endTransaction();
		}
		
		Log.d(LOG_TAG, "do114Upgrade returning");
	}
	
	private void do120Upgrade(SQLiteDatabase database, SQLiteDatabase tempDb) {
		// adds dlm_id column
		
		
		// Move existing video data into temp table (dlm_id will be left at default of 0).
		tempDb.beginTransaction();
		Cursor c = null;
		try {
			tempDb.execSQL("delete from video");
			c = database.rawQuery("select * from video", null);
			while (c.moveToNext()) {
				ContentValues v = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, v);
				tempDb.insert("video", null, v);
			}
			tempDb.setTransactionSuccessful();
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
			tempDb.endTransaction();
		}
			
		// Recreate main video table with the new column.
		database.execSQL("drop table video");
		c = tempDb.rawQuery("select sql from sqlite_master where tbl_name=?", new String[] {"video"});
		while (c.moveToNext()) {
			String s = c.getString(0);
			if (s != null) {
				database.execSQL(s);
			}
		}
		c.close();
		
		// Move data from temp back into main db.
		database.beginTransaction();
		try {
			c = tempDb.rawQuery("select * from video", null);
			while (c.moveToNext()) {
				ContentValues v = new ContentValues();
				DatabaseUtils.cursorRowToContentValues(c, v);
				database.insert("video", null, v);
			}
			database.setTransactionSuccessful();
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
			database.endTransaction();
		}
		
		syncWithDownloadManager();
	}
	
	class TempHelper extends OrmLiteSqliteOpenHelper {
		
		static final String DB_NAME = "temp";

		public TempHelper(Context context) {
		    super(context, DB_NAME, null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		}

		@Override
		public void onUpgrade(SQLiteDatabase database,
				ConnectionSource connectionSource, int oldVersion, int newVersion) {
		}
	}
	
	
	///////////////////////////////  Download-related  ////////////////////////////////
	
	public void syncWithDownloadManager() {
		Log.w(LOG_TAG, "syncWithDownloadManager");
		DownloadManager mgr = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		DownloadManager.Query q = new DownloadManager.Query();
		
		class DownloadInfo {
			DownloadInfo(long id, int status) {
				this.id = id;
				this.status = status;
			}
			final long id;
			final int status;
		}
		
		// Query for all.
		// In case the same video has been downloaded more than once, we want to use the most
		// optimistic one. So on the first pass, set dlm_id and hash youtubeIds against their status,
		// overwriting failed with in progress, and in progress with complete.
		Cursor c = mgr.query(q);
		final Map<String, DownloadInfo> infos = new HashMap<String, DownloadInfo>();
		while (c.moveToNext()) {
			String path = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
			if (path == null) {
				//weird.
				continue;
			}
			String youtubeId = OfflineVideoManager.youtubeIdFromFilename(path);
			long dlm_id = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID));
			int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
			boolean exists = new File(path).exists();
			boolean shouldExist = 0 != (status & (STATUS_PAUSED | STATUS_RUNNING | STATUS_SUCCESSFUL));
			if (!exists && shouldExist) {
				status = STATUS_FAILED;
			}
				
				
			DownloadInfo info = new DownloadInfo(dlm_id, status);
			DownloadInfo existing = infos.get(youtubeId);
			if (existing == null
					|| existing.status == DownloadManager.STATUS_FAILED
					|| existing.status != DownloadManager.STATUS_SUCCESSFUL && info.status == DownloadManager.STATUS_SUCCESSFUL) {
				infos.put(youtubeId, info);
			}
		}
		c.close();
		
		// Now get all the matching videos.
		QueryBuilder<Video, String> qb = videoDao.queryBuilder();
		List<Video> tempVideos = null;
		try {
			qb.where().in("youtube_id", infos.keySet().toArray(new Object[0]));
			tempVideos = qb.query();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		// On the second pass, do the actual updates based on the best completion status we found.
		if (tempVideos != null) {
			final List<Video> videos = tempVideos;
			
			try {
				videoDao.callBatchTasks(new Callable<Integer>() {
					@Override public Integer call() {
						int result = 0;
						
						for (Video video : videos) {
							result++;
							DownloadInfo info = infos.get(video.getYoutube_id());
							video.setDlm_id(info.id);
							
							int status = info.status;
							byte newStatus = -1;
							switch (status) {
							case DownloadManager.STATUS_FAILED:
								removeDownloadFromDownloadManager(video);
								continue;
							case DownloadManager.STATUS_PENDING:
							case DownloadManager.STATUS_PAUSED:
							case DownloadManager.STATUS_RUNNING:
								newStatus = Video.DL_STATUS_IN_PROGRESS;
								break;
							case DownloadManager.STATUS_SUCCESSFUL:
								newStatus = Video.DL_STATUS_COMPLETE;
								break;
							}
							video.setDownload_status(newStatus);
							try {
								videoDao.update(video);
							} catch (SQLException e) {
								e.printStackTrace();
							}
						}
						
						return result;
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		Log.w(LOG_TAG, "  <--   syncWithDownloadManager");
	}
	
	public Video getVideoForFilename(String path) {
		if (path == null) return null;
		
		String youtubeId = OfflineVideoManager.youtubeIdFromFilename(path);
		QueryBuilder<Video, String> q = videoDao.queryBuilder();
		try {
			q.where().eq("youtube_id", youtubeId);
			return q.queryForFirst();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public long removeDownloadFromDownloadManager(Video video) {
		if (video == null) return 0;
		
		long toRemove = video.getDlm_id();
		if (toRemove > 0) {
			((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(toRemove);
			resetDownloadStatusAndDlmId(toRemove);
		}
		return toRemove;
	}
	
	private void resetDownloadStatusAndDlmId(long dlm_id) {
		try {
			UpdateBuilder<Video, String> q = getVideoDao().updateBuilder();
			q.where().eq("dlm_id", dlm_id);
			q.updateColumnValue("dlm_id", 0);
			q.updateColumnValue("download_status", Video.DL_STATUS_NOT_STARTED);
			q.update();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	////////////////////////////////////////////////////////////////////////////////////

	
	/**
	 * Increments downloaded_video_count field on the topic with the given id and all its ancestors.
	 * 
	 * TODO : review the need for this. I think it's moot, as we now calculate download counts for topics on demand.
	 * 
	 * @param db The db in which to do the incrementing.
	 * @param topicId The id of the topic to update.
	 * @param topicTableName The name of the topic table in the given db.
	 */
	public static void incrementDownloadedVideoCounts(SQLiteDatabase db, String topicId, String topicTableName) {
		Log.d(LOG_TAG, "incrementDownloadedVideoCounts: " + topicId);
		String sql = String.format("update %s set downloaded_video_count = downloaded_video_count + 1 where _id=?", topicTableName);
		String[] idArg = new String[] {topicId};
		db.execSQL(sql, idArg);
		
		sql = String.format("select parentTopic_id, downloaded_video_count from %s where _id=?", topicTableName);
		Cursor c = db.rawQuery(sql, idArg);
		c.moveToFirst();
		String parentId = c.getString(c.getColumnIndex("parentTopic_id"));
		String dlc = c.getString(c.getColumnIndex("downloaded_video_count"));
		Log.d(LOG_TAG, "  --> " + dlc);
		c.close();
		if (parentId != null) {
			incrementDownloadedVideoCounts(db, parentId, topicTableName);
		}
	}
	
	
	public Dao<UserVideo, Integer> getUserVideoDao() throws SQLException {
		if (userVideoDao == null) {
			userVideoDao = getDao(UserVideo.class);
		}
		return userVideoDao;
	}
	
	public Dao<User, String> getUserDao() throws SQLException {
		if (userDao == null) {
			userDao = getDao(User.class);
		}
		return userDao;
	}
	
	public Dao<Video, String> getVideoDao() throws SQLException {
		if (videoDao == null) {
			videoDao = getDao(Video.class);
		}
		return videoDao;
	}

	public Dao<Topic, String> getTopicDao() throws SQLException {
		if (topicDao == null) {
			topicDao = getDao(Topic.class);
		}
		return topicDao;
	}
	
	/**
	 * Build a raw SQL string finding the descendant videos of the given topic at exactly the given depth.
	 * 
	 * This returns FROM and WHERE clauses. The SELECT and other parts of the query are the responsibility
	 * of the caller. In particular, it is possible to add AND/OR clauses after the WHERE.
	 * 
	 * @param depth The tree depth at which to search for videos (0 for immediate children).
	 * @return A string with appropriate FROM and WHERE clauses.
	 */
	public String buildVideosForTopicSubQuery(int depth) {
		String from = buildTopicVideoFromClause(depth);
		String where = buildTopicVideoWhereClause(depth);
		stringFormatter.format("%s %s", from, where);
		
		String result = stringBuilder.toString();
		stringBuilder.setLength(0);
		Log.v(LOG_TAG, "returning query string: " + result);
		return result;
		
	}
	
	private String buildTopicVideoFromClause(int depth) {
		if (depth == 0) {
			stringFormatter.format("from %s", TABLE_VIDEO);
		} else {
			stringFormatter.format(", %s as p%%d", TABLE_TOPIC);
			String extra = stringBuilder.toString();
			stringBuilder.setLength(0);
			
			stringFormatter.format("from %s, %s as p0", TABLE_VIDEO, TABLE_TOPIC);
			// for each depth N, add a ", topic as pN"
			for (int i=0; i<depth-1; ++i) {
				stringFormatter.format(extra, i+1);
			}
		}
		
		String result = stringBuilder.toString();
		stringBuilder.setLength(0);
		return result;
	}
	
	private String buildTopicVideoWhereClause(int depth) {
		if (depth == 0) {
			stringFormatter.format("where %s = ?", COL_FK_TOPIC);
		} else {
			stringFormatter.format(" p%%d.%s = p%%d.%s and", COL_FK_TOPIC, COL_TOPIC_ID);
			String extraWhere = stringBuilder.toString();
			stringBuilder.setLength(0);
			
			stringFormatter.format("where %s.%s = p0.%s and", TABLE_VIDEO, COL_FK_TOPIC, COL_TOPIC_ID);
		    // for each depth N, add a "pN.parentTopic_id = pN+1._id and"
			for (int i=0; i<depth-1; ++i) {
				stringFormatter.format(extraWhere, i, i+1);
			}
			stringFormatter.format(" p%d.%s = ?", depth - 1, COL_FK_TOPIC);
		}
		
		String result = stringBuilder.toString();
		stringBuilder.setLength(0);
		return result;
	}
	
	public boolean deleteDatabase(Context context) {
		return context.deleteDatabase(DATABASE_NAME);
	}
	
	public List<Video> getAllDownloadedVideos() {
		Dao<Video, String> videoDao;
		List<Video> result;
		try {
			videoDao = getVideoDao();
			QueryBuilder<Video, String> qb = videoDao.queryBuilder();
			qb.where().ne(COL_DL_STATUS, Video.DL_STATUS_NOT_STARTED);
			result = videoDao.query(qb.prepare());
		} catch (SQLException e) {
			e.printStackTrace();
			result = new ArrayList<Video>();
		}
		return result;
	}
	
	public List<UserVideo> getUserVideos(User user, List<Video> videos) {
		if (user == null) {
			return null;
		}
		List<UserVideo> result = null;
		List<String> ids = new ArrayList<String>(videos.size());
		for (Video v : videos) {
			ids.add(v.getReadable_id());
		}
		try {
			userVideoDao = getUserVideoDao();
			QueryBuilder<UserVideo, Integer> q = userVideoDao.queryBuilder();
			q.where().in("video_id", ids);
			q.where().eq("user_id", user.getNickname());
			result = userVideoDao.query(q.prepare());
		} catch (SQLException e) {
			e.printStackTrace();
			result = new ArrayList<UserVideo>();
		}
		return result;
	}
	
	/**
	 * Update a video's download_status in the database.
	 * 
	 * Proxy to videoDao.update, but will not modify any fields but download_status.
	 * Also updates the downloaded_video_count of all Topics in the video's parent 
	 * hierarchy.
	 * 
	 * @param video The video to update.
	 */
	public void updateDownloadStatus(Video video, int was, int is) {
		Log.d(LOG_TAG, "updateDownloadStatus");
		try {
			Dao<Video, String> videoDao = getVideoDao();
			UpdateBuilder<Video, String> u = videoDao.updateBuilder();
			u.updateColumnValue("download_status", is);
			u.where().eq("youtube_id", video.getYoutube_id());
			videoDao.update(u.prepare());
			
			// DEBUG
//			QueryBuilder<Video, String> q = videoDao.queryBuilder();
//			q.where().eq("readable_id", video.getReadable_id());
//			Video v = videoDao.queryForFirst(q.prepare());
//			Log.d(LOG_TAG, String.format("updated dl status of %s from %d to %d", video.getTitle(), was, v.getDownload_status()));
			
//			Dao<Topic, String> topicDao = getTopicDao();
//			UpdateBuilder<Topic, String> ub = topicDao.updateBuilder();
//			ub.updateColumnExpression("downloaded_video_count", "downloaded_video_count + 1");
//			Topic p = video.getParentTopic();
//			while (p != null) {
//				topicDao.refresh(p);
//				Log.d(LOG_TAG, String.format("updating parent topic %s. Value was %d...", p.getTitle(), p.getDownloaded_video_count()));
//				ub.where().idEq(p.getId());
//				PreparedUpdate<Topic> pu = ub.prepare();
//				topicDao.update(pu);
//				
//				// DEBUG
////				p = topicDao.queryForId(p.getId());
////				Log.d(LOG_TAG, String.format("updated to %d.", p.getDownloaded_video_count()));
//				
//				p = p.getParentTopic();
//			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void denormalizeAncestry() {
		String fmt = "%s|%s";
		try {
			for (Video v : getVideoDao().queryForAll()) {
				String ancestry = "";
				Topic p = v.getParentTopic();
				while (p != null) {
					getTopicDao().refresh(p);
					ancestry = String.format(fmt, p.getId(), ancestry);
					p = p.getParentTopic();
				}
				if (ancestry.endsWith("|")) { // actually, this is true for all videos
					ancestry = ancestry.substring(0, ancestry.length()-1);
				}
				v.setAncestry(ancestry);
				getVideoDao().update(v);
			}
			for (Topic t : getTopicDao().queryForAll()) {
				String ancestry = "";
				Topic p = t.getParentTopic();
				while (p != null) {
					getTopicDao().refresh(p);
					ancestry = String.format(fmt, p.getId(), ancestry);
					p = p.getParentTopic();
				}
				if (ancestry.endsWith("|")) { // true for all but the root topic
					ancestry = ancestry.substring(0, ancestry.length()-1);
				}
				t.setAncestry(ancestry);
				getTopicDao().update(t);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private List<String> getAllDownloadedYoutubeIds() {
		// youtube ids look like this: -h_x8TwC1ik
		String youtubeIdPattern = "^([-_a-zA-Z0-9]{11})(\\.\\d{2,4})?";
		Pattern pattern = Pattern.compile(youtubeIdPattern);
		Matcher matcher;
		
		File file = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		List<String> allFilenames = Arrays.asList(file.list());
		List<String> result = new ArrayList<String>();
		for (String s : allFilenames) {
			matcher = pattern.matcher(s);
			if (matcher.find()) {
				result.add(matcher.group(1));
			}
		}
		return result;
		
	}
	
}
