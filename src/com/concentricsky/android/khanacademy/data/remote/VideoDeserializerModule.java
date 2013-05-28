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
import java.util.Iterator;

import com.concentricsky.android.khanacademy.data.db.Video;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class VideoDeserializerModule extends SimpleModule {
	private static final long serialVersionUID = 1990628478880621620L;

	private ObjectMapper defaultMapper;
	
	public VideoDeserializerModule() {
		super("VideoDeserializerModule", new Version(1, 0, 0, null, "com.concentricsky.android", "khanacademy"));
		this.addDeserializer(Video.class, new VideoDeserializer());
		defaultMapper = new ObjectMapper();
	}
	
	public final class VideoDeserializer extends StdScalarDeserializer<Video> {
		private static final long serialVersionUID = 7599380630777421226L;

		public VideoDeserializer() { super(Video.class); }

		@Override
		public Video deserialize(JsonParser jp, DeserializationContext ctxt)
				throws IOException, JsonProcessingException {
			
			// First, do the default deserialization.
		    TreeNode tree = defaultMapper.readTree(jp);
		    Video video = defaultMapper.treeToValue(tree, Video.class);
		    
		    // Then, in case this video came from the child list of a Topic's detail record, copy the 'id' onto 'readable_id'.
		    try {
		    	// ClassCastException if this is really a NullNode.
		    	// That should never happen. I think it was just happening because I was accidentally trying to consume the video twice.
		    	ObjectNode root = (ObjectNode) tree;
			    Iterator<String> keys = root.fieldNames();
			    
			    while (keys.hasNext()) {
			    	String key = keys.next();
			    	if ("id".equals(key)) {
			    		JsonNode value = root.get(key);
			    		if (value.isTextual()) {
			    			video.setReadable_id(value.asText());
			    		}
			    	}
			    }
		    } catch (ClassCastException e) {
		    	e.printStackTrace();
		    	// fall through and return video unchanged.
		    }
			
			return video;
		}

	}
	
}
