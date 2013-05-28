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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@JsonIgnoreProperties(ignoreUnknown=true)
@DatabaseTable
public class Caption {
	
	@DatabaseField(generatedId=true)
	private int _id;
	
	@DatabaseField
	private String youtube_id;
	
	@DatabaseField
	private int start_time;
	
	@DatabaseField
	private int end_time;
	
	@DatabaseField
	private String time_string;
	
	@DatabaseField(dataType=DataType.FLOAT)
	private float sub_order;
	
	@DatabaseField
	private String text;
	
	public int get_id() { return _id; }
	public void set_id(int _id) { this._id = _id; }
	
	public String getYoutube_id() { return youtube_id; }
	public void setYoutube_id(String youtube_id) { this.youtube_id = youtube_id; }
	
	public int getStart_time() { return start_time; }
	public void setStart_time(int time) {
		this.start_time = time;
		int seconds = time / 1000;
		int s = seconds % 60;
		int m = seconds / 60;
		time_string = String.format("%02d:%02d", m, s);
	}

	public int getEnd_time() { return end_time; }
	public void setEnd_time(int end_time) { this.end_time = end_time; }
	
	public String getTime_string() { return time_string; }
	public void setTime_string(String time_string) { this.time_string = time_string; }
	
	public float getSub_order() { return sub_order; }
	public void setSub_order(float sub_order) { this.sub_order = sub_order; }
	
	public String getText() { return text; }
	public void setText(String text) { this.text = text.replace('\n', ' '); }
	
}