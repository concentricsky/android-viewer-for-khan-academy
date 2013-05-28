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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.j256.ormlite.field.DatabaseField;

/**
 * Common base class for all 
 * @author austinlally
 *
 */
@JsonTypeInfo(  
    use = JsonTypeInfo.Id.NAME,  
    include = JsonTypeInfo.As.PROPERTY,  
    property = "kind",
    
    // XXX : Hack to get UserVideos to deserialize properly. Not sure if it has to do with the JsonSubType name not 
    // matching the class name or what, but with this set to EntityBase.Impl we get errors about EntityBase.Impl not
    // having a single-string constructor (despite the 'user' field of a UserVideo being declared as User type).
    defaultImpl = User.class
    )
@JsonSubTypes({
    @Type(value = User.class, name = "UserData"),  
    @Type(value = UserVideo.class, name = "UserVideo"),  
    @Type(value = EntityBase.class, name = "Video"),  
    @Type(value = EntityBase.class, name = "Topic")
    })  
@JsonIgnoreProperties(ignoreUnknown=true)
public class ModelBase {

	@DatabaseField
	String kind;
	
	/**
	 * @return the kind
	 */
	public String getKind() {
		return kind;
	}

	/**
	 * @param kind the kind to set
	 */
	public void setKind(String kind) {
		this.kind = kind;
	}

	@JsonAnySetter
	public void setUnknownKey(String key, Object value) {
		
	}
	
}
