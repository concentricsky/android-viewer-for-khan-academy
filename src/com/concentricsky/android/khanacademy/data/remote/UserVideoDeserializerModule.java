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

import com.concentricsky.android.khanacademy.data.db.UserVideo;
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

public class UserVideoDeserializerModule extends SimpleModule {
	private static final long serialVersionUID = 1990628478880621620L;

	private ObjectMapper defaultMapper;
	
	public UserVideoDeserializerModule() {
		super("UserVideoDeserializerModule", new Version(1, 0, 0, null, "com.concentricsky.android", "khanacademy"));
		this.addDeserializer(UserVideo.class, new UserVideoDeserializer());
		defaultMapper = new ObjectMapper();
	}
	
	public final class UserVideoDeserializer extends StdScalarDeserializer<UserVideo> {
		private static final long serialVersionUID = 5540210306258763322L;

		public UserVideoDeserializer() { super(UserVideo.class); }

		@Override
		public UserVideo deserialize(JsonParser jp, DeserializationContext ctxt)
				throws IOException, JsonProcessingException {
			
		    TreeNode tree = defaultMapper.readTree(jp);
		    
	    	ObjectNode root = (ObjectNode) tree;
		    Iterator<String> keys = root.fieldNames();
		    
		    while (keys.hasNext()) {
		    	String key = keys.next();
		    	if ("video".equals(key)) {
		    		JsonNode value = root.get(key);
		    		if (value.isObject()) {
		    			root.set(key, value.get("readable_id"));
		    		}
		    	}
		    }
			
		    UserVideo video = defaultMapper.treeToValue(tree, UserVideo.class);
			return video;
		}

	}
	
}
