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
import java.net.URI;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

public class SpringRequestFactory implements ClientHttpRequestFactory {
	
	private OAuthConsumer consumer;
	
	public SpringRequestFactory(OAuthConsumer consumer) {
		this.consumer = consumer;
	}
	
	@Override
	public ClientHttpRequest createRequest(URI uri,
			HttpMethod httpMethod) throws IOException {
		
		/*
		 * Note: This is a bit ugly, and it comes from working with two different libraries.
		 * This could probably be cleaned up by integrating Spring's auth library and using
		 * that instead of OAuth Signpost. Signpost was here first though, and works well 
		 * enough.
		 */
		
		// build a default request
		SimpleClientHttpRequestFactory defaultFactory = new SimpleClientHttpRequestFactory();
		ClientHttpRequest request = defaultFactory.createRequest(uri, httpMethod);
		
		// now build a bogus CommonsHttp request, sign it, and grab the headers.
		HttpGet bogusRequest = new HttpGet(uri);
		
		try {
			consumer.sign(bogusRequest);
		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
		}
		
		// finally, apply those headers to our request, and return it
		Header[] headers = bogusRequest.getAllHeaders();
		for (Header h : headers) {
			request.getHeaders().set(h.getName(), h.getValue());
		}
		
		return request;
	}
}
