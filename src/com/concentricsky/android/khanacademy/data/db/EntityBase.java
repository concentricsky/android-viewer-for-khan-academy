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

import com.concentricsky.android.khanacademy.data.remote.BaseEntityUpdateVisitor;
import com.concentricsky.android.khanacademy.data.remote.EntityVisitor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.j256.ormlite.field.DatabaseField;

@JsonIgnoreProperties(ignoreUnknown=true)
@JsonTypeInfo(  
    use = JsonTypeInfo.Id.NAME,  
    include = JsonTypeInfo.As.PROPERTY,  
    property = "kind",
    visible = true,
    defaultImpl = EntityBase.Impl.class
    )
@JsonSubTypes({
    @Type(value = Video.class, name = "Video"),  
    @Type(value = Topic.class, name = "Topic")
    })  
public abstract class EntityBase extends ModelBase {
	
	public static class Impl extends EntityBase {
		
		@Override public int getDownloaded_video_count() { return 0; }
		@Override public int getVideo_count() { return 0; } 
		
		@Override
		public String getId() {
			throw new UnsupportedOperationException("But Videos and Topics have ids...");
		}
		
		@Override
		public BaseEntityUpdateVisitor<Impl> buildUpdateVisitor() {
			return new BaseEntityUpdateVisitor<Impl>(this) { };
		}
		
		@Override
		public void accept(EntityVisitor visitor) {
			visitor.visit(this);
		}
	}
	
	@DatabaseField
	String title;
	
	@DatabaseField
    String description;
	
	@DatabaseField
    String ka_url;
	
	@DatabaseField
    String hide;
	
	@DatabaseField(foreign=true)
	Topic parentTopic;
	
	/** |-separated list of the topics above this element, beginning with "root" and ending with this element's immediate parent */
	@DatabaseField
	String ancestry;
	
	/** Sequencing of this element within its parent topic. For ORDER BY in list views. */
	@DatabaseField
	int seq;

	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}
	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	/**
	 * @return the ka_url
	 */
	public String getKa_url() {
		return ka_url;
	}
	/**
	 * @param ka_url the ka_url to set
	 */
	public void setKa_url(String ka_url) {
		this.ka_url = ka_url;
	}
	/**
	 * @return the hide
	 */
	public String getHide() {
		return hide;
	}
	/**
	 * @param hide the hide to set
	 */
	public void setHide(String hide) {
		this.hide = hide;
	}
	/**
	 * @return the parentTopic
	 */
	public Topic getParentTopic() {
		return parentTopic;
	}

	/**
	 * @param parentTopic the parentTopic to set
	 */
	public void setParentTopic(Topic parentTopic) {
		this.parentTopic = parentTopic;
//		parentTopic.getChildren().add(this);
	}

	public String getAncestry() {
		return ancestry;
	}

	public void setAncestry(String ancestry) {
		this.ancestry = ancestry;
	}

	abstract public int getDownloaded_video_count();
	abstract public int getVideo_count();
	abstract public String getId();
	abstract public BaseEntityUpdateVisitor<? extends EntityBase> buildUpdateVisitor();
	abstract public void accept(EntityVisitor visitor);
}
