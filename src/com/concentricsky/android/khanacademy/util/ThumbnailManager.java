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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ResponseCache;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.support.v4.util.LruCache;

import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Thumbnail;
import com.jakewharton.DiskLruCache;

/**
 * Handles downloading thumbnails, caching them, and returning them to the list fragment.
 * 
 * 
 * @author austinlally
 *
 */
public class ThumbnailManager {
	
	public static final String LOG_TAG = ThumbnailManager.class.getSimpleName();
	private static final int CONNECT_TIMEOUT = 3000;

	private static ThumbnailManager sharedInstance;
	
	/* ***********************   PRIVATE   ***************************/
	
	private final KADataService dataService;
	private final ConnectivityManager connectivityManager;
	private final LruCache<Thumbnail, Bitmap> cache;
	private final DiskLruCache diskCache;
	
	private boolean isDestroyed;
	
	public static ThumbnailManager getSharedInstance(KADataService dataService) {
		if (sharedInstance == null || sharedInstance.isDestroyed) {
			sharedInstance = new ThumbnailManager(dataService);
		}
		return sharedInstance;
	}
	
	private ThumbnailManager(final KADataService dataService) {
		this.dataService = dataService;
		connectivityManager = (ConnectivityManager) dataService.getSystemService(Context.CONNECTIVITY_SERVICE);

		cache = prepareCache();
		diskCache = prepareDiskCache();
	}
	
	private DiskLruCache prepareDiskCache() {
	    int v = 0;
	    try {
	        v = dataService.getPackageManager().getPackageInfo(dataService.getPackageName(), 0).versionCode;
	    } catch (NameNotFoundException e) {
	        // Huh? Really?
	    }
	    
	    // TODO : allow user to configure this.
	    long maxSize = 1024 * 1024 * 1024;
	    
	    File cacheDir = new File(dataService.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "thumbnail_cache");
	    int valueCount = 8;
	    
		try {
			// TODO : This is slow on first run. Look into improving that.
			return DiskLruCache.open(cacheDir, v, valueCount, maxSize);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private LruCache<Thumbnail, Bitmap> prepareCache() {
		// Total available heap size. This changes based on manifest android:largeHeap="true". (Fire HD, transformer both go from 48MB to 256MB)
		Runtime rt = Runtime.getRuntime();
		long maxMemory = rt.maxMemory();
		Log.v(LOG_TAG, "maxMemory:" + Long.toString(maxMemory));
		
		// Want to use at most about 1/2 of available memory for thumbs.
		// In SAT Math category (116 videos), with a heap size of 48MB, this setting
		// allows 109 thumbs to be cached resulting in total heap usage around 34MB.
		long usableMemory = maxMemory / 2;
		
		return new LruCache<Thumbnail, Bitmap>((int) usableMemory) {
			@Override
			protected int sizeOf(Thumbnail key, Bitmap value) {
				return value.getByteCount();
			}
			
			@Override
			protected void entryRemoved(boolean evicted, Thumbnail key, Bitmap oldValue, Bitmap newValue) {
				if (oldValue != newValue) {
					oldValue.recycle();
				}
			}
		};
		
	}
	
	/* ***********************   PUBLIC   ***************************/

	public void destroy() {
		cache.evictAll();
		if (diskCache != null) {
			try {
				diskCache.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		isDestroyed = true;
	}
	
	/**
	 * Cache of thumbnails by youtube id.
	 * @return
	 */
	public LruCache<Thumbnail, Bitmap> getCache() {
		return cache;
	}
	
	/**
	 * Get the thumbnail for the video with the given youtube id, or start a download if it isn't yet stored locally.
	 * 
	 * @param y_id The youtube id of the video whose thumbnail we need.
	 * @return The thumbnail as a {@link Bitmap}, or {@code null} if none exists.
	 */
	
	public Bitmap getThumbnail(String y_id, byte quality, boolean useCache) {
		
		Bitmap result = null;
		
		if (useCache) {
			Thumbnail thumbnail = new Thumbnail(y_id, quality);
			result = cache.get(thumbnail);
		}
		
		if (result == null) {
			result = getThumbnail(y_id, quality);
		}
		return result;
	}
	
	private int indexForAvailability(byte q) {
		switch (q) {
		case Thumbnail.QUALITY_LOW:
			return 4;
		case Thumbnail.QUALITY_MEDIUM:
			return 5;
		case Thumbnail.QUALITY_HIGH:
			return 6;
		case Thumbnail.QUALITY_SD:
			return 7;
		default:
			throw new IllegalArgumentException("invalid thumb quality");
		}
	}
	private int indexForQuality(byte q) {
		switch (q) {
		case Thumbnail.QUALITY_LOW:
			return 0;
		case Thumbnail.QUALITY_MEDIUM:
			return 1;
		case Thumbnail.QUALITY_HIGH:
			return 2;
		case Thumbnail.QUALITY_SD:
			return 3;
		default:
			throw new IllegalArgumentException("invalid thumb quality");
		}
	}
	
	public Bitmap getThumbnailFromDiskCache(String youtubeId, byte quality) {
		String key = youtubeId.toLowerCase(Locale.US);
		Bitmap result = null;
		DiskLruCache.Snapshot snap = null;
		DiskLruCache.Editor editor = null;
		
		// Ensure we have a cache entry for this youtube id.
		try {
			// null while another editor is open
			while ((editor = diskCache.edit(key)) == null) { }
			
			if (editor.getString(indexForAvailability(Thumbnail.QUALITY_HIGH)) == null) {
				// values only null if they've never been set, so this must be a new entry
				editor.set(indexForQuality(Thumbnail.QUALITY_HIGH), "");
				editor.set(indexForAvailability(Thumbnail.QUALITY_HIGH), String.valueOf(Thumbnail.AVAILABILITY_UNKNOWN));
				editor.set(indexForQuality(Thumbnail.QUALITY_MEDIUM), "");
				editor.set(indexForAvailability(Thumbnail.QUALITY_MEDIUM), String.valueOf(Thumbnail.AVAILABILITY_UNKNOWN));
				editor.set(indexForQuality(Thumbnail.QUALITY_LOW), "");
				editor.set(indexForAvailability(Thumbnail.QUALITY_LOW), String.valueOf(Thumbnail.AVAILABILITY_UNKNOWN));
				editor.set(indexForQuality(Thumbnail.QUALITY_SD), "");
				editor.set(indexForAvailability(Thumbnail.QUALITY_SD), String.valueOf(Thumbnail.AVAILABILITY_UNKNOWN));
				editor.commit();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (editor != null) editor.abortUnlessCommitted();
		}
		
		while (quality >= Thumbnail.QUALITY_LOW && result == null) {
			
			try {
				// Try getting a bitmap for this quality from disk.
				snap = diskCache.get(key);
				InputStream is = snap.getInputStream(indexForQuality(quality));
				try {
					result = BitmapFactory.decodeStream(is);
				} finally {
					if (is != null) {
						try { is.close(); } catch (IOException ex) { }
					}
				}
				if (result != null) {
					return result;
				}
			
				// If none exists, try fetching it if we haven't before.
				int availability = Integer.parseInt(snap.getString(indexForAvailability(quality)));
				if (availability != Thumbnail.AVAILABILITY_UNAVAILABLE) {
					NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
					if (activeNetwork != null && activeNetwork.isConnected()) {
						try {
							result = bitmap_from_url(Thumbnail.getDownloadUrl(dataService, quality, youtubeId));
						} catch (MalformedURLException e) {
							e.printStackTrace();
						} catch (IOException e) {
							// FileNotFoundException on 404. Mark as unavailable.
							if (e instanceof FileNotFoundException) {
								try {
									while ((editor = diskCache.edit(key)) == null) { }
									editor.set(indexForAvailability(quality), String.valueOf(Thumbnail.AVAILABILITY_UNAVAILABLE));
									editor.commit();
								} catch (IOException ex) {
									ex.printStackTrace();
								} finally {
									if (editor != null) editor.abortUnlessCommitted();
								}
							} else {
								e.printStackTrace();
							}
						}
					}
					
					// If we receive a thumbnail response, store it in the cache and return it.
					if (result != null) {
						try {
							while ((editor = diskCache.edit(key)) == null) { }
							OutputStream os = editor.newOutputStream(indexForQuality(quality));
							try {
								result.compress(Bitmap.CompressFormat.PNG, 100, os);
							} finally {
								if (os != null) os.close();
							}
							editor.set(indexForAvailability(quality), String.valueOf(Thumbnail.AVAILABILITY_AVAILABLE));
							editor.commit();
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (editor != null) editor.abortUnlessCommitted();
						}
						return result;
					}
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (snap != null) snap.close();
			}
			
			quality--;
		}
		
		return result;
	}
	
	public Bitmap getThumbnail(String y_id, byte quality) {
		Log.v(LOG_TAG, ".getThumbnailForYoutubeId");
		return getThumbnailFromDiskCache(y_id, quality);
	}

	/**
	 * Attempt to download a bitmap from the given url.
	 * 
	 * @param url The url of the thumbnail to download.
	 * @return A {@link Bitmap} of the thumbnail, or {@code null} if it cannot be downloaded and is not cached locally.
	 * @throws java.net.MalformedURLException if the url is malformed!
	 * @throws java.io.IOException if a connection cannot be opened to the given url, or if an IOException is thrown by {@link ResponseCache} or by {@link CacheResponse}.
	 */
	public static Bitmap bitmap_from_url(String url) throws java.net.MalformedURLException, IOException {
	    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
	    connection.setConnectTimeout(CONNECT_TIMEOUT);
	    connection.setUseCaches(true);
	    
		InputStream input = null;
	    try {
	    	connection.connect();
		    input = connection.getInputStream();
		    return BitmapFactory.decodeStream(input);
	    } catch (SocketTimeoutException e) {
	    	e.printStackTrace();
	    	return null;
	    } finally {
	    	if (input != null) input.close();
	    }
	}
	
	
	/*
	 "media$thumbnail": [
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/default.jpg",
			"height": 90,
			"width": 120,
			"time": "00:08:31",
			"yt$name": "default"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/mqdefault.jpg",
			"height": 180,
			"width": 320,
			"yt$name": "mqdefault"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/hqdefault.jpg",
			"height": 360,
			"width": 480,
			"yt$name": "hqdefault"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/sddefault.jpg",
			"height": 480,
			"width": 640,
			"yt$name": "sddefault"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/1.jpg",
			"height": 90,
			"width": 120,
			"time": "00:04:15.500",
			"yt$name": "start"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/2.jpg",
			"height": 90,
			"width": 120,
			"time": "00:08:31",
			"yt$name": "middle"
		},
		{
			"url": "http://i.ytimg.com/vi/l-QFT7XNeb4/3.jpg",
			"height": 90,
			"width": 120,
			"time": "00:12:46.500",
			"yt$name": "end"
		}
	],
	*/
	

}
