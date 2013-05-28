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
package com.concentricsky.android.khanacademy.util;

import static com.concentricsky.android.khanacademy.Constants.ACTION_DOWNLOAD_PROGRESS_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.ACTION_TOAST;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_MESSAGE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_STATUS;

import java.io.File;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.DatabaseHelper;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;


/**
 * Handles video downloads, tracks which have been downloaded, and offers callbacks for
 * download progress updates and for updates when an offline video has been added or deleted.
 * 
 * Interfaces with the Android {@link DownloadManager} service.
 * 
 * @author austinlally
 *
 */


/*
 * TODO :
 * 
 *  - Decide whether to allow downloads to continue when we're not running (and whether to start the next
 *    enqueued download when not running). Maybe offer a user preference. If not, similarly decide whether
 *    to automatically resume when the app is launched.
 *  - Add interface to cancel (all/individual) (running/enqueued) downloads.
 *  
 */



public class OfflineVideoManager {

	public static final String LOG_TAG = OfflineVideoManager.class.getSimpleName();
	
	private static final long MIN_ERROR_TOAST_INTERVAL = 500;
	private static final Pattern filenamePattern = Pattern.compile("([-_a-zA-Z0-9]{11})\\.mp4");

	
	/* ***********************  PRIVATE  ****************************/
	
	private final Handler handler = new Handler();
	private final ExecutorService pollerExecutor = Executors.newSingleThreadExecutor();
	private ExecutorService queueExecutor = Executors.newSingleThreadExecutor();
	
	private final KADataService dataService;
	private final Dao<Video, String> videoDao;
	private final LocalBroadcastManager broadcastManager;
	
	private volatile boolean shouldPoll = false;
	
	private Poller currentPoller;
	private FileObserver fileObserver;
	private long lastErrorToastTime;

	/**
	 * Poll the DownloadManager for progress of current downloads, then trigger a broadcast.
	 * 
	 * Not a particularly lengthy operation, but it happens a lot and we want it on a background thread for maximum smoothness.
	 */
	private class Poller extends AsyncTask<Void, Void, HashMap<String, Integer>> {
		
		private static final String sql = "select dlm_id from video where dlm_id > 0";
		
		private final DownloadManager.Query q = new DownloadManager.Query();

		@Override
		protected HashMap<String, Integer> doInBackground(Void... arg0) {
			// get an array of ids, for use in the download manager query
			Cursor c = dataService.getHelper().getReadableDatabase().rawQuery(sql, null);
			long[] ids = new long[c.getCount()];
			while (c.moveToNext()) {
				ids[c.getPosition()] = c.getLong(c.getColumnIndex("dlm_id"));
			}
			c.close();
		
			if (ids.length > 0) {
				q.setFilterById(ids);
				q.setFilterByStatus(DownloadManager.STATUS_RUNNING);
				
				Cursor cursor = getDownloadManager().query(q);
				HashMap<String, Integer> update = new HashMap<String, Integer>(cursor.getCount());
				while (cursor.moveToNext()) {
					String filename = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
					String youtubeId = youtubeIdFromFilename(filename);
					int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
					if (youtubeId != null) {
						int pct = -1;
						switch (status) {
						case DownloadManager.STATUS_FAILED:
						case DownloadManager.STATUS_PENDING:
						default:
							pct = 0;
							break;
						case DownloadManager.STATUS_PAUSED:
						case DownloadManager.STATUS_RUNNING:
							long bytes_downloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
							long size = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
							pct = (int) (size==0? 0: 100 * bytes_downloaded / size);
							break;
						case DownloadManager.STATUS_SUCCESSFUL:
							pct = 100;
							break;
						}
						update.put(youtubeId, pct);
					}
				}
				cursor.close();
				
				return update;
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(HashMap<String, Integer> update) {
			if (update != null) {
				doDownloadProgressUpdate(update);
			}
			// We've just finished polling. This will be set true again soon enough if a download
			// is in progress; we'll get a MODIFY event in the DownloadsObserver.
			currentPoller = null;
			shouldPoll = false;
		}
	}
	
	private Runnable pollerPoller = new Runnable() {
		@Override
		public void run() {
			if (shouldPoll && currentPoller == null) {
				currentPoller = new Poller();
				currentPoller.executeOnExecutor(pollerExecutor);
			}
			handler.postDelayed(this, 1000);
		}
	};
	
	/**
	 * Watches our downloads directory for changes.
	 * 
	 * This lets us know if a user has deleted a video or moved it away while the
	 * app is running. Update the db, set download_status to NOT_STARTED. For changes
	 * while we're NOT running, just check on startup.
	 * 
	 * This can also give us an indication that a current download has been paused
	 * via CLOSE_WRITE.
	 * 
	 * We know a download has begun the first time we see OPEN after it is enqueued.
	 */
	private final class DownloadsObserver extends FileObserver {
		
		 // From http://rswiki.csie.org/lxr/http/source/include/linux/inotify.h?a=m68k#L45
//			 29 #define IN_ACCESS               0x00000001      /* File was accessed */
//			 30 #define IN_MODIFY               0x00000002      /* File was modified */
//			 31 #define IN_ATTRIB               0x00000004      /* Metadata changed */
//			 32 #define IN_CLOSE_WRITE          0x00000008      /* Writtable file was closed */
//			 33 #define IN_CLOSE_NOWRITE        0x00000010      /* Unwrittable file closed */
//			 34 #define IN_OPEN                 0x00000020      /* File was opened */
//			 35 #define IN_MOVED_FROM           0x00000040      /* File was moved from X */
//			 36 #define IN_MOVED_TO             0x00000080      /* File was moved to Y */
//			 37 #define IN_CREATE               0x00000100      /* Subfile was created */
//			 38 #define IN_DELETE               0x00000200      /* Subfile was deleted */
//			 39 #define IN_DELETE_SELF          0x00000400      /* Self was deleted */
//			 40 #define IN_MOVE_SELF            0x00000800      /* Self was moved */
//			 41 
//			 42 /* the following are legal events.  they are sent as needed to any watch */
//			 43 #define IN_UNMOUNT              0x00002000      /* Backing fs was unmounted */
//			 44 #define IN_Q_OVERFLOW           0x00004000      /* Event queued overflowed */
//			 45 #define IN_IGNORED              0x00008000      /* File was ignored */
//			 46 
//			 47 /* helper events */
//			 48 #define IN_CLOSE                (IN_CLOSE_WRITE | IN_CLOSE_NOWRITE) /* close */
//			 49 #define IN_MOVE                 (IN_MOVED_FROM | IN_MOVED_TO) /* moves */
//			 50 
//			 51 /* special flags */
//			 52 #define IN_ONLYDIR              0x01000000      /* only watch the path if it is a directory */
//			 53 #define IN_DONT_FOLLOW          0x02000000      /* don't follow a sym link */
//			 54 #define IN_EXCL_UNLINK          0x04000000      /* exclude events on unlinked objects */
//			 55 #define IN_MASK_ADD             0x20000000      /* add to the mask of an already existing watch */
//			 56 #define IN_ISDIR                0x40000000      /* event occurred against dir */
//			 57 #define IN_ONESHOT              0x80000000      /* only send event once */
//		
//			 64 #define IN_ALL_EVENTS   (IN_ACCESS | IN_MODIFY | IN_ATTRIB | IN_CLOSE_WRITE | \
//					 65                          IN_CLOSE_NOWRITE | IN_OPEN | IN_MOVED_FROM | \
//					 66                          IN_MOVED_TO | IN_DELETE | IN_CREATE | IN_DELETE_SELF | \
//					 67                          IN_MOVE_SELF)
	
			
		private static final int flags =
				FileObserver.CLOSE_WRITE
				| FileObserver.OPEN
				| FileObserver.MODIFY
				| FileObserver.DELETE
				| FileObserver.MOVED_FROM;
		// Received three of these after the delete event while deleting a video through a separate file manager app:
		// 01-16 15:52:27.627: D/APP(4316): DownloadsObserver: onEvent(1073741856, null)
		// This is 0x40000020 : IN_ISDIR|IN_OPEN
		
		public DownloadsObserver(String path) {
			super(path, flags);
		}

		@Override
		public void onEvent(int event, final String path) {
			if (path == null) {
				return;
			}
			
			switch (event & FileObserver.ALL_EVENTS) {
			case FileObserver.CLOSE_WRITE:
				// Download complete, or paused when wifi is disconnected. Possibly reported more than once in a row.
				// Useful for noticing when a download has been paused. For completions, register a receiver for 
				// DownloadManager.ACTION_DOWNLOAD_COMPLETE.
				break;
			case FileObserver.OPEN:
				// Called for both read and write modes.
				// Useful for noticing a download has been started or resumed.
				break;
			case FileObserver.DELETE:
			case FileObserver.MOVED_FROM:
				// This video is lost never to return. Remove it.
				handler.post(new Runnable() {
					@Override
					public void run() {
						DatabaseHelper helper = dataService.getHelper();
						helper.removeDownloadFromDownloadManager(helper.getVideoForFilename(path));
					}
				});
				break;
			case FileObserver.MODIFY:
				// Called very frequently while a download is ongoing (~1 per ms).
				// This could be used to trigger a progress update, but that should probably be done less often than this.
				shouldPoll = true;
				break;
			}
		}
		
	}
	
	/**
	 * Creates a {@link DownloadProgressPoller} and a background thread for running it. Registers
	 * our {@link BroadcastReceiver} for {@link DownloadManager} updates.
	 * 
	 * @param dataService A {@link Context} with which to register our {@link BroadcastReceiver}.
	 */
	public OfflineVideoManager(KADataService dataService) {
		this.dataService = dataService;
		broadcastManager = LocalBroadcastManager.getInstance(dataService);
		
		try {
			videoDao = dataService.getHelper().getVideoDao();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		fileObserver = new DownloadsObserver(this.getDownloadDir().getAbsolutePath());
		fileObserver.startWatching();
		
		handler.postDelayed(pollerPoller, 1000);
		resume();
	}

	/* ***********************  PUBLIC   ****************************/

	/**
	 * Populate our lists of downloaded and downloading videos from
	 * {@link DownloadManager}.
	 */
	public void resume() {
		dataService.getHelper().syncWithDownloadManager();
	}
	
	/**
	 * Unregister callbacks.
	 */
	public void destroy() {
		fileObserver.stopWatching();
		handler.removeCallbacks(pollerPoller);
	}
	
	/**
	 * Cancel ongoing and enqueued video downloads.
	 */
	public void cancelAllVideoDownloads() {
		final DownloadManager dlm = getDownloadManager();
		final DownloadManager.Query q = new DownloadManager.Query();
		q.setFilterByStatus(DownloadManager.STATUS_FAILED | DownloadManager.STATUS_PAUSED
				| DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING);
		
		// Cancel all tasks - we don't want any more downloads enqueued, and we are
		// beginning a cancel task so we don't need any previous one.
		queueExecutor.shutdownNow();
		queueExecutor = Executors.newSingleThreadExecutor();
		
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected void onPreExecute() {
				doToast("Stopping downloads...");
			}
			@Override
			protected Integer doInBackground(Void... arg) {
				int result = 0;
				if (isCancelled()) return result;
				
				Cursor c = dlm.query(q);
				Long[] removed = new Long[c.getCount()];
				int i = 0;
				while (c.moveToNext()) {
					if (isCancelled()) break;
					
					long id = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID));
					removed[i++] = id;
					dlm.remove(id);
					result++;
				}
				c.close();
				
				UpdateBuilder<Video, String> u = videoDao.updateBuilder();
				try {
					u.where().in("dlm_id", (Object[]) removed);
					u.updateColumnValue("download_status", Video.DL_STATUS_NOT_STARTED);
					u.update();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				
				return result;
			}
			@Override
			protected void onPostExecute(Integer result) {
				if (result > 0) {
					doToast(result + " downloads cancelled.");
				} else {
					doToast("No downloads in queue.");
				}
				doOfflineVideoSetChanged();
			}
			@Override
			protected void onCancelled(Integer result) {
				if (result > 0) {
					doToast(result + " downloads cancelled.");
				}
				doOfflineVideoSetChanged();
			}
		}.executeOnExecutor(queueExecutor);
	}
	
	/**
	 * Deletes the OfflineVideos from the filesystem. Cancels downloads if in progress.
	 * 
	 * @param toDelete the OfflineVideos to delete
	 */
	public void deleteOfflineVideos(Set<Video> toDelete) {
		if (toDelete == null || toDelete.size() == 0) return;
		
		// Remove from download manager if applicable, and update internal state.
		for (Video v : toDelete) {
			dataService.getHelper().removeDownloadFromDownloadManager(v);
		}
		
		// Update listeners and continue downloading.
		doToast(dataService.getString(R.string.msg_deleted));
		doOfflineVideoSetChanged();
	}
	
	/**
	 * Begin a video download by creating a {@link DownloadManager.Request} 
	 * and enqueuing it with the {@link DownloadManager}.
	 * 
	 * @param video The {@link Video} to download.
	 * @return False if external storage could not be loaded and thus the download cannot begin, true otherwise.
	 */
	public boolean downloadVideo(Video video) {
		// TODO 
//		dataService.getCaptionManager().getCaptionsForYoutubeId(video.getYoutube_id());
		
		Log.d(LOG_TAG, "downloadVideo");
		
		if (hasDownloadBegun(video)) return true;
		
		File file = getDownloadDir();
		if (file != null) {
			file = new File(file, getFilenameForOfflineVideo(video));
			file.delete(); //if it exists -- otherwise, this does nothing.
			Log.d(LOG_TAG, "starting download to file: " + file.getPath());
			DownloadManager mgr = getDownloadManager();
			String url = video.getMp4url();
			
			if (url == null) {
				//WHYYYY
				//TODO toast there is no download url for this video --- can we even view it? which video is this??
				// TODO : try m3u8 instead?
				Log.e(LOG_TAG, "NO DOWNLOAD URL FOR VIDEO " + video.getTitle());
				return false;
			}
			
			Uri requestUri = Uri.parse(url);
			DownloadManager.Request request = new DownloadManager.Request(requestUri);
			Uri localUri = Uri.fromFile(file);
	        request.setDestinationUri(localUri);
			request.setDescription(file.getName());
			request.setTitle(video.getTitle());
			request.setVisibleInDownloadsUi(false);
			long id = mgr.enqueue(request);
			Log.d(LOG_TAG, "download request ENQUEUED: " + id);
			
			// mark download as begun
			try {
				videoDao.refresh(video);
				video.setDownload_status(Video.DL_STATUS_IN_PROGRESS);
				video.setDlm_id(id);
				videoDao.update(video);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			return true;
		} else {
			showDownloadError();
			return false;
		}
	}
	
	public void downloadAll(final Collection<Video> videos) {
		Log.d(LOG_TAG, "downloadAll (" + videos.size() + ")");
		final int size = videos.size();
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected void onPreExecute() {
				doToast(String.format("Downloading %d videos...", size));
			}
			@Override
			protected Integer doInBackground(Void... arg) {
				int result = 0;
				for (Video v : videos) {
					if (isCancelled()) break;
					
					result++;
					downloadVideo(v);
				}
				return result;
			}
		}.executeOnExecutor(queueExecutor);
	}
	
	/**
	 * Show a toast explaining that a {@link Video} could not be downloaded. 
	 */
	private void showDownloadError() {
		long time = SystemClock.uptimeMillis();
		if (time - lastErrorToastTime < MIN_ERROR_TOAST_INTERVAL) return;
		lastErrorToastTime = time;
		doToast("Error writing to storage. Please try again later.");
	}
	
	
	
	
	
	private void doToast(String message) {
		Intent intent = new Intent(ACTION_TOAST);
		intent.putExtra(EXTRA_MESSAGE, message);
		broadcastManager.sendBroadcast(intent);
	}
	
	private void doDownloadProgressUpdate(HashMap<String, Integer> youtubeIdToPct) {
		Log.d(LOG_TAG, "doDownloadProgressUpdate");
		Intent intent = new Intent(ACTION_DOWNLOAD_PROGRESS_UPDATE);
		intent.putExtra(EXTRA_STATUS, youtubeIdToPct);
		broadcastManager.sendBroadcast(intent);
	}
	
	/**
	 * Refresh our offline video lists, and call 
	 * {@link OfflineVideoSetChangedListener#onOfflineVideoSetChanged()} 
	 * on each registered {@link OfflineVideoSetChangedListener}.
	 */
	private void doOfflineVideoSetChanged() {
		Log.d(LOG_TAG, "Offline Video Set Changed");
		
		Intent intent = new Intent(Constants.ACTION_OFFLINE_VIDEO_SET_CHANGED);
		broadcastManager.sendBroadcast(intent);
	}
	
	
	
	
	
	
	
	/**
	 * Get whether a {@link Video} has begun downloading.
	 * 
	 * @param video The {@link Video} to check.
	 * @return True if the download has begun, false otherwise.  This means true if a download is complete, and false if the download is enqueued but not yet begun.
	 */
	public boolean hasDownloadBegun(Video video) {
		Log.d(LOG_TAG, "hasDownloadBegun");
		DownloadManager.Query q = new DownloadManager.Query();
		long dlm_id = video.getDlm_id();
		if (dlm_id <= 0) {
			return false;
		}
		q.setFilterById(dlm_id);
		q.setFilterByStatus(DownloadManager.STATUS_PAUSED
				| DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_SUCCESSFUL);
		Cursor c = getDownloadManager().query(q);
		boolean result = c.getCount() > 0;
		c.close();
		return result;
	}
	
	/**
	 * Get a filename for the given {@link Video}.
	 * 
	 * @param v The {@link Video} for which to get a filename.
	 * @return The filename as a String.
	 */
	private String getFilenameForOfflineVideo(Video v) {
		return v.getYoutube_id() + ".mp4";
	}
	
	private String buildDownloadCountQuery(int depth) {
		Log.d(LOG_TAG, "buildQuery");
		String sql;
		if (depth == 0) {
			sql = "select count(video._id) from video,topicvideo where topicvideo.topic_id=? and topicvideo.video_id = video.readable_id";
		} else if (depth == 1) {
			sql = "select count(video._id) from video,topicvideo,topic as t1 where t1.parentTopic_id=? and topicvideo.topic_id=t1._id and topicvideo.video_id = video.readable_id";
		} else {
		
			// Depth 2~n
			sql = "select count(video._id) from video,topicvideo ,topic as t1";
			for (int i = 2; i < depth; ++i) {
				sql += String.format(",topic as t%d ", i);
			}
			sql += " where topicvideo.topic_id=t1._id and topicvideo.video_id=video.readable_id and t1.parentTopic_id=";
			
			for (int i = 2; i < depth; ++i) {
				sql += String.format("t%d._id and t%d.parentTopic_id=", i, i);
			}
			sql += "?";
			// Output of above with depth=6
	//		String out = "select count(video._id) from video,topicvideo ,topic as t1,topic as t2 ,topic as t3 ,topic as t4 ,topic as t5 " +
	//				"where topicvideo.topic_id=t1._id and topicvideo.video_id=video.readable_id  and t1.parentTopic_id=t2._id and t2.parentTopic_id=t3._id and t3.parentTopic_id=t4._id and t4.parentTopic_id=t5._id and t5.parentTopic_id=?";
			
			// Samples with "root"
			// depth = 1
	//		sql = "select count(video._id) from video,topicvideo,topic as t1 " +
	//				"where topicvideo.video_id = video.readable_id and topicvideo.topic_id=t1._id and t1.parentTopic_id='root'";
			
			// depth = 2
	//		sql = "select count(video._id) from video,topicvideo,topic as t1,topic as t2 " +
	//				"where topicvideo.video_id = video.readable_id and topicvideo.topic_id=t1._id and t1.parentTopic_id=t2._id and t2.parentTopic_id='root'  ";
		
		}
		
		sql += " and video.download_status=" + String.valueOf(Video.DL_STATUS_COMPLETE);
		
		Log.d(LOG_TAG, "  --> " + sql);
		return sql;
	}
	
	public int getDownloadCountForTopic(SQLiteOpenHelper dbh, String topicId, int depth) {
		Log.d(LOG_TAG, "getDownloadCountForTopic");
		
		int result = 0;
		SQLiteDatabase db = dbh.getReadableDatabase();
		
		for (int i = 0; i < depth; ++i) {
			String sql = buildDownloadCountQuery(i);
			Cursor c = db.rawQuery(sql, new String[] {topicId});
			c.moveToFirst();
			result += c.getInt(0);
			Log.d(LOG_TAG, " result is " + result);
			c.close();
		}
		
		return result;
	}
	
	
	
	
	public static String youtubeIdFromFilename(String filename) {
		if (filename == null) return null;
		
		String youtubeId = null;
		Matcher m = filenamePattern.matcher(filename);
		if (m.find()) {
			youtubeId = m.group(1);
		}
		return youtubeId;
	}
	
	
	
	private DownloadManager getDownloadManager() {
		return (DownloadManager) dataService.getSystemService(Context.DOWNLOAD_SERVICE);
	}
	
	private File getDownloadDir() {
		File file = dataService.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		return file;
	}
	
}
