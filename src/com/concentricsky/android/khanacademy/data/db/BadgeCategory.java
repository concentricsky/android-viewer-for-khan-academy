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

import com.concentricsky.android.khan.R;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
@JsonIgnoreProperties(ignoreUnknown=true)
public class BadgeCategory implements Serializable {

	private static final long serialVersionUID = 5099215302476746630L;

	@DatabaseField(id=true)
	private int id;
	
	@DatabaseField
	private String name;
	
	@DatabaseField
	private String description;
	
	
	public BadgeCategory() { }
	
	public BadgeCategory(int id) {
		this.id = id;
	}
	
	public BadgeCategory(String name) {
		this.name = name;
	}
	
	public int getIconResourceId() {
		switch (getId()) {
		case 1:
			return R.drawable.badge_1_large;
		case 2:
			return R.drawable.badge_2_large;
		case 3:
			return R.drawable.badge_3_large;
		case 4:
			return R.drawable.badge_4_large;
		case 5:
			return R.drawable.badge_5_large;
		case 0:
		default:
			return R.drawable.badge_0_large;
		}
	}
	
	@JsonProperty("category")
	public int getId() { return id; }
	public void setId(int id) { this.id = id; }

	@JsonProperty("type_label")
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	
}
