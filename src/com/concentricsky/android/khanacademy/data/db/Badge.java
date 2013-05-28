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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a badge earned by a user, including description, points, category, etc.
 * 
 * @author austinlally
 *
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class Badge implements Serializable {
	
	private static final long serialVersionUID = -6480730546297718390L;
	
	private BadgeCategory category;
	private String description;
	private String safe_extended_description;
	private int points;
	
	
	@JsonProperty("badge_category")
	public BadgeCategory getCategory() {
		return category;
	}


	public String getDescription() {
		return description;
	}


	public String getSafe_extended_description() {
		return safe_extended_description;
	}


	public int getPoints() {
		return points;
	}


	/**
	 * @param category the category to set
	 */
	public void setCategory(BadgeCategory category) {
		this.category = category;
	}


	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}


	/**
	 * @param safe_extended_description the safe_extended_description to set
	 */
	public void setSafe_extended_description(String safe_extended_description) {
		this.safe_extended_description = safe_extended_description;
	}


	/**
	 * @param points the points to set
	 */
	public void setPoints(int points) {
		this.points = points;
	}

}

