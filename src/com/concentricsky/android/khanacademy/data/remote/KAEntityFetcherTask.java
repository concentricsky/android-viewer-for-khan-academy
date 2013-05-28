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

import oauth.signpost.OAuthConsumer;

import org.springframework.http.converter.json.replacement.MappingJacksonHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import android.os.AsyncTask;

import com.concentricsky.android.khanacademy.data.db.ModelBase;
import com.concentricsky.android.khanacademy.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A task that makes a single API call, serializes the result, and passes it to onPostExecute.
 * 
 * I toyed with making this generic to return the correct type, but there is no clean place
 * to catch any resulting exception if the api call returns an unexpected type. Best leave
 * the casting to the caller.
 * 
 * @author austinlally
 *
 */
public abstract class KAEntityFetcherTask<T extends ModelBase> extends AsyncTask<Void, Integer, T> {
	
	public final String LOG_TAG = getClass().getName();
	private String url;
	private OAuthConsumer consumer;
	
	protected Exception exception;

	public KAEntityFetcherTask(String url) {
		this.url = url;
	}
	
	public KAEntityFetcherTask(String url, OAuthConsumer consumer) {
		this(url);
		this.consumer = consumer;
	}
	
	@Override
	protected T doInBackground(Void... arg0) {
		// call  API and fetch an entity tree (commonly the tree rooted at the root topic)
		
		RestTemplate restTemplate = new RestTemplate();
		if (consumer != null) {
			restTemplate.setRequestFactory(new SpringRequestFactory(consumer));
		}
		
		// TODO : Set up stream parsing.
		
//		RequestCallback callback = new RequestCallback() {
//
//			public void doWithRequest(ClientHttpRequest request)
//					throws IOException {
//				// TODO Auto-generated method stub
//				
//			}
//			
//		};
//		
//		ResponseExtractor<T> extractor = new ResponseExtractor<T>() {
//
//			public T extractData(ClientHttpResponse response)
//					throws IOException {
//				
//				InputStream stream = response.getBody();
//				
//				
//				return null;
//			}
//			
//		};
//		
//		restTemplate.execute(url, HttpMethod.GET, requestCallback, responseExtractor)
		
		
		// Provide a converter to the restTemplate to get automagic json --> pojo deserialization.
		// Provide a mapper to the converter so we can register the custom Video deserializer. Otherwise, the default ObjectMapper would do fine.
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new VideoDeserializerModule());
		MappingJacksonHttpMessageConverter converter = new MappingJacksonHttpMessageConverter();
		converter.setObjectMapper(mapper);
		restTemplate.getMessageConverters().add(converter);
		
		ModelBase entity = null;
		try {
		    entity = restTemplate.getForObject(this.url, ModelBase.class);
		} catch (HttpClientErrorException e) {
			e.printStackTrace();
			exception = e;
			// meanwhile, entity is null, so we let that return naturally.
		} catch (ResourceAccessException e) {
			// This one happens on Nexus 7 if we have set a SpringRequestFactory but get no Auth challenge.
			// org.springframework.web.client.ResourceAccessException: I/O error: No authentication challenges found; nested exception is java.io.IOException: No authentication challenges found
			e.printStackTrace();
			Log.e(LOG_TAG, "url was " + url);
			exception = e;
		}
		
	    
	    T result;
	    try {
	    	result = (T) entity;
	    } catch (ClassCastException e) {
	    	e.printStackTrace();
	    	exception = e;
	    	result = null;
	    }
	    
	    Log.d(LOG_TAG, "Response received. Returning entity.");
	    return result;
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
	protected abstract void onPostExecute(T result);

}
