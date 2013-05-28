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

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.http.client.ClientProtocolException;

import android.webkit.WebResourceResponse;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.db.Caption;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

/**
 * Handles retrieving subtitles from the web, caching them, and returning them to the video fragment.
 * 
 * @author austinlally
 *
 */
public class CaptionManager {
	/* ***********************   STATIC   ***************************/
	
	public static final String LOG_TAG = CaptionManager.class.getSimpleName();

	public static final int CONNECT_TIMEOUT = 5000;
	
	/* ***********************   PRIVATE   ***************************/
	
	private KADataService dataService;
	private String subtitleFormat;
	
	/* ***********************   PUBLIC   ***************************/

	/**
	 * Get a CaptionManager to manage your captions!
	 * 
	 * @param context Any context will do. Used to look up a string resource, and no reference is kept.
	 */
	public CaptionManager(KADataService dataService) {
		this.dataService = dataService;
		subtitleFormat = dataService.getString(R.string.url_format_subtitles);
	}
	
	/**
	 * Get a {@link WebResourceResponse} with subtitles for the video with the given youtube id.
	 * 
	 * The response contains a UTF-8 encoded json object with the subtitles received 
	 * from universalsubtitles.org. 
	 * 
	 * @param youtubeId The youtube id of the video whose subtitles we need.
	 * @return The {@link WebResourceResponse} with the subtitles, or {@code null} in case of error or if none are found.
	 */
	public WebResourceResponse fetchRawCaptionResponse(String youtubeId) {
		Log.d(LOG_TAG, "fetchRawCaptionResponse");
		String youtube_url = "http://www.youtube.com/watch?v=" + youtubeId;
		try {
			URL url = new URL(String.format(subtitleFormat, youtube_url, "en"));
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setUseCaches(true);
			InputStream in = null;
			try {
				in = connection.getInputStream();
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
				//various exceptions including at least ConnectException and UnknownHostException can happen if we're offline
			}
			
			return in==null? null: new WebResourceResponse("application/json", "UTF-8", in);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public List<Caption> getCaptions(String youtubeId) {
		Log.d(LOG_TAG, "getCaptions: " + youtubeId);
		
		List<Caption> result = null;
		Dao<Caption, Integer> captionDao = null;
		try {
			captionDao = dataService.getHelper().getDao(Caption.class);
			QueryBuilder<Caption, Integer> q = captionDao.queryBuilder();
			q.where().eq("youtube_id", youtubeId);
			q.orderBy("sub_order", true);
			// TODO : Avoid inserting duplicates in the first place, and do a migration to clean up.
			q.groupBy("sub_order");
			result = q.query();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if (result != null && result.size() > 0) {
			Log.d(LOG_TAG, " already cached; returning");
			return result;
		}
		
		// If we do not already have the captions, try fetching them.
		// Clients will call this in a background thread, so we can take our time.
		WebResourceResponse response = fetchRawCaptionResponse(youtubeId);
		result = parseAPIResponse(response);
		result = pruneEmptyCaptions(result);
		result = persist(result, youtubeId);
		return result;
	}
	
	private List<Caption> parseAPIResponse(WebResourceResponse response) {
		Log.d(LOG_TAG, "parseAPIResponse");
		List<Caption> result = null;
		if (response != null) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				result = mapper.readValue(response.getData(), new TypeReference<List<Caption>>(){});
				Log.d(LOG_TAG, " result length is " + result.size());
			} catch (JsonParseException e) {
				// At 5pm on a Thursday, I encountered this exception. Did a little digging, and the response turned
				// out to contain this: 
				// <html> <body> <div style="text-align: center; padding-top: 200px">
				//  	Amara is currently unavailable for scheduled maintenance. The site will be back shortly.
				// </div> </body> </html>
				
				// Another at 2:45 Tuesday:  Illegal character ((CTRL-CHAR, code 31)): only regular white space (\r, \n, \t) is allowed between tokens
				// This has happened more than once around the same time. Caught it again today, Tue 12/4, at about 3:00.
				
				// At any rate, these all fall into the "failed to download" category rather than the "none exist" category.
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			Log.d(LOG_TAG, " response was null");
		}
		return result;
	}
	
	private List<Caption> pruneEmptyCaptions(List<Caption> toPrune) {
		Log.d(LOG_TAG, "pruneEmptyCaptions");
		// Ensure captions have times. See long comment below for explanation.
		// Otherwise, return null and pretend there were no captions.
		List<Caption> result = null;
		if (toPrune != null) {
			for (Caption c : toPrune) {
//				Log.d(LOG_TAG, String.format("%03.2f %10d %s", c.getSub_order(), c.getStart_time(), c.getText()));
				if (c.getStart_time() > 0) {
					result = toPrune;
					break;
				}
			}
		}
		
		// prune individual empty captions
		if (result != null && result.size() > 0) {
			List<Caption> toRemove = new ArrayList<Caption>();
			for (Caption caption : result) {
				if (caption.getText().trim().length() == 0) {
					toRemove.add(caption);
				}
			}
			for (Caption caption : toRemove) {
				result.remove(caption);
			}
		}
		return result;
	}
	
	private List<Caption> persist(final List<Caption> toSave, final String youtubeId) {
		try {
			final Dao<Caption, Integer> captionDao = dataService.getHelper().getDao(Caption.class);
			
			if (captionDao != null && toSave != null && toSave.size() > 0) {
				// Batching speeds this up significantly.
				captionDao.callBatchTasks(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						for (Caption c : toSave) {
							try {
								c.setYoutube_id(youtubeId);
								captionDao.create(c);
							} catch (SQLException e) {
								e.printStackTrace();
							}
						}
						return null;
					}
				});
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return toSave;
	}
	
		/*  Example of a response that isn't quite what we want.
		 *  In these cases, we just pretend there are no subtitles at all, as an incomplete list without time stamps isn't that useful.
		 * [
    {
        "subtitle_id": "wcpvewparc82371936",
        "text": "What I want to do in this video is show you that some of the things that we've been talking about ",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 1.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "ivddkbpjit82695805",
        "text": "on the last few videos actually do happen, and in particular,",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 2.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "avfjvqzffm83421791",
        "text": "talk about how one of these speculative attacks on a currency can turn into",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 3.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "fmfcpugiut83483196",
        "text": "a banking crisis! This is a chart",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 4.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "otacwxlpyh83540836",
        "text": "from Oxford Economics, and it shows two things:",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 5.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "yyqivgduxz83628118",
        "text": "Thailand's exchange rate and short-term interest rates from the early",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 6.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "rtbbbazapm83760974",
        "text": "1990's to the present, there's a couple of interesting",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 7.0,
        "start_of_paragraph": false
    },
    {
        "subtitle_id": "ccxmomlhbe83829991",
        "text": "things that you might see over here; the first is the exchange rate, you see",
        "start_time": -1,
        "end_time": -1,
        "sub_order": 8.0,
        "start_of_paragraph": false
    }
]
		 */
}
