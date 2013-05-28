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
package com.concentricsky.android.khanacademy.data;

import static com.concentricsky.android.khanacademy.Constants.ACTION_LIBRARY_UPDATE;
import static com.concentricsky.android.khanacademy.Constants.ACTION_UPDATE_DOWNLOAD_STATUS;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_FORCE;
import static com.concentricsky.android.khanacademy.Constants.EXTRA_ID;
import static com.concentricsky.android.khanacademy.Constants.RESULT_CODE_FAILURE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.db.DatabaseHelper;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.Video;
import com.concentricsky.android.khanacademy.data.remote.KAAPIAdapter;
import com.concentricsky.android.khanacademy.data.remote.LibraryUpdaterTask;
import com.concentricsky.android.khanacademy.util.CaptionManager;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.concentricsky.android.khanacademy.util.OfflineVideoManager;
import com.concentricsky.android.khanacademy.util.ThumbnailManager;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.UpdateBuilder;

/**
 * Manages all data for the application.
 * 
 * This service holds a reference to a database Helper, the Khan Academy API adapter,
 * and a local storage adapter for downloaded videos. It also sets a default HTTP
 * response cache.
 * 
 * We implement both onStartCommand and onBind.  Activities should bind to this service
 * with bindService to use it as a persistent data backend.  Meanwhile, we can call
 * startService to start long-running operations like video downloads and library
 * updates.
 * 
 * @author austinlally
 *
 */
public class KADataService extends Service {
    
	public final String LOG_TAG = getClass().getSimpleName();
	
	public static final int RESULT_SUCCESS = 0;
	public static final int RESULT_ERROR = 1;
	public static final int RESULT_CANCELLED = 2;
	
	private KADataBinder mBinder = new KADataBinder(this);
    private DatabaseHelper helper;
    private KAAPIAdapter api;
    private CaptionManager captionManager;
    private OfflineVideoManager offlineVideoManager;
    private ThumbnailManager thumbnailManager;
    
    private Executor libraryUpdateExecutor = Executors.newSingleThreadExecutor();
    private NotificationManager notificationManager;
    
    /**
     * Passed as the IBinder parameter to ServiceConnection.onServiceConnected when a
     * consumer binds to this service.
     * 
     * Must be static:
     * http://code.google.com/p/android/issues/detail?id=6426
     */
    public static class KADataBinder extends Binder {
    	private KADataService dataService;
    	public KADataBinder(KADataService dataService) {
    		setDataService(dataService);
    	}
        public KADataService getService() {
            return dataService;
        }
        public void setDataService(KADataService dataService) {
    		this.dataService = dataService;
        }
    }
	
    /**
     * Thrown by Provider instances when the service is not (yet) available.
     */
	public static class ServiceUnavailableException extends Exception {
		private static final long serialVersionUID = 581386365380491650L;
		public final boolean expected;
		public ServiceUnavailableException(boolean expected) {
			super();
			this.expected = expected;
		}
	}
    
	/**
	 * Simplified accessor interface.
	 */
    public interface Provider {
    	public KADataService getDataService() throws ServiceUnavailableException;
    	public boolean requestDataService(ObjectCallback<KADataService> callback);
    	public boolean cancelDataServiceRequest(ObjectCallback<KADataService> callback);
    }

    @Override
    public void onCreate() {
    	Log.d(LOG_TAG, "onCreate");
    	
    	helper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
    	
    	InputStream is = getResources().openRawResource(R.raw.oauth_credentials);
    	byte[] buffer;
		try {
			buffer = new byte[is.available()];
	    	while (is.read(buffer) != -1);
	    	String jsontext = new String(buffer);
	    	JSONObject oauthCredentials = new JSONObject(jsontext);
	    	String key = oauthCredentials.getString("OAUTH_CONSUMER_KEY");
	    	String secret = oauthCredentials.getString("OAUTH_CONSUMER_SECRET");
	    	api = new KAAPIAdapter(this, key, secret);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
    	
    	captionManager = new CaptionManager(this);
    	offlineVideoManager = new OfflineVideoManager(this);
    	thumbnailManager = ThumbnailManager.getSharedInstance(this);
    	notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    	
    	setupResponseCache();
    }
        
    /**
     * Used for long-running operations including library updates and video downloads.
     * 
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("KADataService", "Received start id " + startId + ": " + intent);
        
        PendingIntent pendingIntent = null;
        if (intent.hasExtra(Intent.EXTRA_INTENT)) {
        	// TODO : use this intent. It needs to be called with the results of requestLibraryUpdate (so far)
        	pendingIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        }
        
        if (ACTION_LIBRARY_UPDATE.equals(intent.getAction())) {
        	requestLibraryUpdate(startId, pendingIntent, intent.getBooleanExtra(EXTRA_FORCE, false));
        	return START_REDELIVER_INTENT;
        }
        
        if (ACTION_UPDATE_DOWNLOAD_STATUS.equals(intent.getAction())) {
        	Log.d(LOG_TAG, "update download status");
        	updateDownloadStatus(intent, pendingIntent, startId);
        	return START_REDELIVER_INTENT;
        }
        
        /*
        START_NOT_STICKY
		Do not recreate the service, unless there are pending intents to deliver. This is the 
		safest option to avoid running your service when not necessary and when your application 
		can simply restart any unfinished jobs.
		START_STICKY
		Recreate the service and call onStartCommand(), but do not redeliver the last intent. 
		Instead, the system calls onStartCommand() with a null intent, unless there were pending 
		intents to start the service, in which case, those intents are delivered. This is suitable 
		for media players (or similar services) that are not executing commands, but running 
		indefinitely and waiting for a job.
		START_REDELIVER_INTENT
		Recreate the service and call onStartCommand() with the last intent that was delivered 
		to the service. Any pending intents are delivered in turn. This is suitable for services 
		that are actively performing a job that should be immediately resumed, such as downloading a file.
         */
        
        // If we reach this point, the intent has some unknown action, so just ignore it and stop.
        this.stopSelfResult(startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
    	Log.d(LOG_TAG, "onDestroy");
    	
    	api = null;
    	captionManager = null;
    	
    	offlineVideoManager.destroy();
    	offlineVideoManager = null;
    	
    	thumbnailManager.destroy();
    	thumbnailManager = null;
    	
    	OpenHelperManager.releaseHelper();
    	flushResponseCache();
    	
    	mBinder.setDataService(null);
    	mBinder = null;
    	notificationManager = null;
    	
    	super.onDestroy();
    }

    /**
     * Used for maintaining a connection to this service to use it as a data backend.
     */
    @Override
    public IBinder onBind(Intent intent) {
    	Log.d(LOG_TAG, "onBind");
        return mBinder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
    	Log.d(LOG_TAG, "onUnbind");
    	return super.onUnbind(intent);
    }
    
    
    /*
     *  Database Access
     */

    public DatabaseHelper getHelper() {
    	return helper;
    }
    
    private void setupResponseCache() {
    	// directly from http://developer.android.com/reference/android/net/http/HttpResponseCache.html
    	try {
    		File httpCacheDir = new File(getCacheDir(), "http");
    		long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
    		Class.forName("android.net.http.HttpResponseCache")
	    		.getMethod("install", File.class, long.class)
	    		.invoke(null, httpCacheDir, httpCacheSize);
    	} catch (Exception httpResponseCacheNotAvailable) {
    		// TODO : set cache the old way
            httpResponseCacheNotAvailable.printStackTrace();
    	}
    }
    
    private void flushResponseCache() {
    	
    	// TODO : check if cache was set the old way, and flush that one.
    	
    	try {
	    	Class<?> cls = Class.forName("android.net.http.HttpResponseCache");
	    	
			Object cache = cls
	    		.getMethod("getInstalled")
	    		.invoke(null);
			
	        if (cache != null) {
	        	cls.getMethod("flush").invoke(cache);
	        }
    	} catch (Exception e) {
    		// Swallow.
            e.printStackTrace();
    	}
    }

	private void finish(int startId, PendingIntent pendingIntent, int result) {
		if (pendingIntent != null) {
			try {
				pendingIntent.send(result);
			} catch (CanceledException e) {
				// Ignore this. If they don't want their result, they don't get it.
			}
		}
		this.stopSelfResult(startId);
	}
    
	private void broadcastLibraryUpdateNotification() {
		Intent intent = new Intent();
		intent.setAction(ACTION_LIBRARY_UPDATE);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	private void broadcastOfflineVideoSetChanged() {
		LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
		Intent intent = new Intent(Constants.ACTION_OFFLINE_VIDEO_SET_CHANGED);
		broadcastManager.sendBroadcast(intent);
	}
	
	private void showUpdateNotification() {
		Notification notification = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.icon_72)
				.setContentTitle("Khan Academy content update")
				.setContentText("Updating content for Viewer for Khan Academy")
				.setOngoing(true)
				.build();
	    notificationManager.notify(1, notification);
	}
	
	private void updateDownloadStatus(Intent intent, final PendingIntent pendingIntent, final int startId) {
    	final long id = intent.getLongExtra(EXTRA_ID, -1);
		final DownloadManager mgr = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
		final DownloadManager.Query q = new DownloadManager.Query();
		q.setFilterById(id);

    	new AsyncTask<Void, Void, Boolean>() {
    		@Override
    		protected Boolean doInBackground(Void... arg) {
				Cursor cursor = mgr.query(q);
				String youtubeId = null;
				int status = -1;
				if (cursor.moveToFirst()) {
					String filename = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
					youtubeId = OfflineVideoManager.youtubeIdFromFilename(filename);
					status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
				}
				cursor.close();
				
				if (status == DownloadManager.STATUS_SUCCESSFUL && youtubeId != null) {
					try {
						Dao<Video, String> videoDao = helper.getVideoDao();
						UpdateBuilder<Video, String> q = videoDao.updateBuilder();
						q.where().eq("youtube_id", youtubeId);
						q.updateColumnValue("download_status", Video.DL_STATUS_COMPLETE);
						q.update();
						return true;
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				
				return false;
    		}
    		
    		@Override
    		protected void onPostExecute(Boolean successful) {
    			if (successful) {
					broadcastOfflineVideoSetChanged();
					finish(startId, pendingIntent, RESULT_SUCCESS);
    			} else {
    				finish(startId, pendingIntent, RESULT_ERROR);
    			}
    		}
    	}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		
	}
	
	private void cancelUpdateNotification() {
	    notificationManager.cancel(1);
	}
	
    private void requestLibraryUpdate(final int startId, final PendingIntent pendingIntent, boolean force) {
    	Log.d(LOG_TAG, "requestLibraryUpdate");
    	
    	showUpdateNotification();
    	LibraryUpdaterTask task = new LibraryUpdaterTask(this) {
    		@Override
    		public void onPostExecute(Integer status) {
    			if (status != RESULT_CODE_FAILURE) {
    				try {
						helper.getDao(Topic.class).clearObjectCache();
	    				helper.getDao(Video.class).clearObjectCache();
					} catch (SQLException e) {
						e.printStackTrace();
					}
    				
    				broadcastLibraryUpdateNotification();
    			} else {
    				Log.w(LOG_TAG, "library update failure code");
    			}
    				
				finish(startId, pendingIntent, RESULT_SUCCESS);
				cancelUpdateNotification();
    		}
    	};
    	task.force = force;
    	task.executeOnExecutor(libraryUpdateExecutor);
    	
    	Log.d(LOG_TAG, "Returning from requestLibraryUpdate");
    }
    
    public KAAPIAdapter getAPIAdapter() {
    	return api;
    }
    
    public CaptionManager getCaptionManager() {
    	return captionManager;
    }

    public OfflineVideoManager getOfflineVideoManager() {
    	return offlineVideoManager;
    }
    
    public ThumbnailManager getThumbnailManager() {
    	return thumbnailManager;
    }
    
    
    /*
     * Data access
     */
    
    public Topic getRootTopic() {
    	Topic topic = null;
		try {
			Dao<Topic, String> topicDao = helper.getTopicDao();
			PreparedQuery<Topic> q = topicDao.queryBuilder().where().isNull("parentTopic_id").prepare();
			topic = topicDao.queryForFirst(q);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return topic;
    }
    
}
