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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.OAuthConsumer;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import android.os.AsyncTask;

import com.concentricsky.android.khanacademy.data.db.ModelBase;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;


public abstract class KAEntityCollectionFetcherTask<T extends ModelBase>
		extends AsyncTask<Void, Integer, List<T>> {
	
//	private TypeReference<List<T>> type;
//	private JavaType type;
	private CollectionType type;
	private String url;
	private OAuthConsumer consumer;
	
	protected Exception exception;
	
	public KAEntityCollectionFetcherTask(Class<T> type, String url) {
		this.url = url;
		TypeFactory f = TypeFactory.defaultInstance();
//		this.type = new TypeReference<List<T>>() {};
//		this.type = f.constructParametricType(ArrayList.class, type);
		this.type = f.constructCollectionType(ArrayList.class, type);
	}
	
	public KAEntityCollectionFetcherTask(Class<T> type, String url, OAuthConsumer consumer) {
		this(type, url);
		this.consumer = consumer;
	}

	@Override
	protected List<T> doInBackground(Void... arg0) {
		// call  API and fetch an entity tree (commonly the tree rooted at the root topic)
		
		RestTemplate restTemplate = new RestTemplate();
		if (consumer != null) {
			restTemplate.setRequestFactory(new SpringRequestFactory(consumer));
		}
		
		restTemplate.getMessageConverters().add(new StringHttpMessageConverter());
		ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);
		String body = result.getBody();
		
		// String tag = "~~~~~~~~~~~~~~~~";
		// Log.setLevel(tag, Log.VERBOSE);
		// Log.d(tag, "result body is a " + body.getClass().getCanonicalName());
		// Log.d(tag, "result is " + body);
		
		ObjectMapper mapper = new ObjectMapper();
		
		List<T> list = null;
		try {
			list = mapper.readValue(body, type);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return list;
	}
	
	/**
	 * Called with the result of the api call. Subclasses should override this 
	 * method to handle the result. If result is null, it means the api call
	 * returned an unexpected object type, and the exception is available on 
	 * this.exception.
	 * 
	 * @param result The return of the api call.
	 */
	@Override
	protected abstract void onPostExecute(List<T> result);

}
