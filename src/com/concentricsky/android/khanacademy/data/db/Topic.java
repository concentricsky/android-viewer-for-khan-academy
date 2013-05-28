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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import com.concentricsky.android.khanacademy.data.remote.BaseEntityUpdateVisitor;
import com.concentricsky.android.khanacademy.data.remote.EntityVisitor;
import com.concentricsky.android.khanacademy.util.Log;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@JsonIgnoreProperties(ignoreUnknown=true)
@DatabaseTable
public class Topic extends EntityBase implements Comparable<Topic>, Serializable {
	public static final String LOG_TAG = Topic.class.getSimpleName();
/*
 *     Full Topic 
 *     
    
    // Always in both
    "kind": "Topic",
    "title": "The Root of All Knowledge",
    
    // Full version of both
    "description": "All concepts fit into the root of all knowledge",
    "relative_url": "/#root",
    "ka_url": "http://www.khanacademy.org/#root",
    "backup_timestamp": "2012-10-16T22:05:27Z",
    
    // Topic only
    "community_questions_title": null,
    "community_questions_url": null,
    "tags": [],
    "init_custom_stack": null,
    "id": "root",
    "assessment_progress_key": "awOEn1j7Rz0sCb-JrZu5agXkun6N382jJwX5fWYEo",
    "topic_page_url": "/",
    "hide": true,
    "extended_slug": "",
    "standalone_title": "The Root of All Knowledge",
    "children": [

 */
	
	/*
	 *    Topic as child
	 *    
	 
	        "kind": "Topic",
            "hide": false,
            "title": "New and Noteworthy",
            "url": "http://www.khanacademy.org/#new-and-noteworthy",
            "key_id": null,
            "id": "new-and-noteworthy"

	 */
	
	private static final long serialVersionUID = -4876433391343270374L;
	
	public static final String CHILD_KIND_VIDEO = "Video";
	public static final String CHILD_KIND_TOPIC = "Topic";
	public static final String CHILD_KIND_NONE = "CHILD_KIND_NONE";
	public static final String CHILD_KIND_UNKNOWN = "CHILD_KIND_???";

	// columnName="_id" for CursorAdapter use.
	@DatabaseField(id=true, columnName="_id")
	String id;
	
	@DatabaseField
	String child_kind;
	
	@DatabaseField
	int video_count;
	
	@DatabaseField
	int downloaded_video_count;
	
	/** Youtube Id of the video whose thumbnail we will display with this topic. 
	 * This will be the first video descendant of this topic that has a thumbnail url). */
	@DatabaseField
	String thumb_id;
	
	@DatabaseField
    String standalone_title;
	
    Collection<EntityBase> children;
    
    @ForeignCollectionField(eager=false)
    Collection<Topic> childTopics;
    
    @ForeignCollectionField(eager=false)
    Collection<Video> childVideos;
    
	/**
	 * @return the id
	 */
	@Override
	public String getId() {
		return id;
	}
	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}
	public String getChild_kind() {
		return child_kind;
	}
	public void setChild_kind(String child_kind) {
		this.child_kind = child_kind;
	}
	@Override
	public int getVideo_count() {
		return video_count;
	}
	public void setVideo_count(int video_count) {
		this.video_count = video_count;
	}
	/**
	 * @return the downloaded_video_count
	 */
	@Override
	public int getDownloaded_video_count() {
		return downloaded_video_count;
	}
	/**
	 * @param downloaded_video_count the downloaded_video_count to set
	 */
	public void setDownloaded_video_count(int downloaded_video_count) {
		this.downloaded_video_count = downloaded_video_count;
	}
	public String getThumb_id() {
		return thumb_id;
	}
	public void setThumb_id(String thumb_id) {
		this.thumb_id = thumb_id;
	}
	/**
	 * @return the standalone_title
	 */
	public String getStandalone_title() {
		return standalone_title;
	}
	/**
	 * @param standalone_title the standalone_title to set
	 */
	public void setStandalone_title(String standalone_title) {
		this.standalone_title = standalone_title;
	}
	
	public Collection<? extends EntityBase> getChildren() {
		Log.d(LOG_TAG, String.format("getChildren: %s", getId()));
		
		if (children != null && children.size() > 0) {
			// This must have been set on this object by Jackson.
			return children;
		}
		
		Collection<Video> childVideos = getChildVideos();
		Log.d(LOG_TAG, String.format("  childVideos is %s", childVideos == null ? "null" : String.valueOf(childVideos.size())));
		if (childVideos != null && childVideos.size() > 0) {
			return childVideos;
		}
		Collection<Topic> childTopics = getChildTopics();
		Log.d(LOG_TAG, String.format("  childTopics is %s", childTopics == null ? "null" : String.valueOf(childTopics.size())));
		if (childTopics != null && childTopics.size() > 0) {
			return childTopics;
		}
		return new ArrayList<EntityBase>();
	}
	public void setChildren(Collection<EntityBase> children) {
		this.children = children;
		for (EntityBase child : children) {
			child.setParentTopic(this);
		}
	}
	
	
	/**
	 * Implements Comparable<Topic>
	 */
	@Override
	public int compareTo(Topic other) {
		return getId().compareTo(other.getId());
	}
	
	@Override
	public boolean equals(Object other) {
		try {
			return ((Topic) other).getId().equals(getId());
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return (getClass().hashCode() + getId().hashCode()) % Integer.MAX_VALUE;
	}
	
	@Override
	public BaseEntityUpdateVisitor<Topic> buildUpdateVisitor() {
		return new BaseEntityUpdateVisitor<Topic>(this) {
			@Override
			public void visit(Topic toUpdate) {
				super.visit(toUpdate);
				
				String value = getChild_kind();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setChild_kind(value);
				}
				
				value = getStandalone_title();
				if (!isDefaultValue(value, String.class)) {
					toUpdate.setStandalone_title(value);
				}
			}
		};
	}
	
	@Override
	public void accept(EntityVisitor visitor) {
		visitor.visit(this);
	}
	public Collection<Topic> getChildTopics() {
		return childTopics;
	}
	public Collection<Video> getChildVideos() {
		return childVideos;
	}
}
