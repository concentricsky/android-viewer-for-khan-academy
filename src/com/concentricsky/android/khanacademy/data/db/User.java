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

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@JsonIgnoreProperties(ignoreUnknown=true)
@DatabaseTable(tableName = "user")
public class User extends ModelBase {
	
	/*
{
    "all_proficient_exercises": [
        "addition_1", 
        "subtraction_1", 
        "multiplication_0.5"
    ], 
    "badge_counts": {
        "0": 1, 
        "1": 1, 
        "2": 0, 
        "3": 0, 
        "4": 0, 
        "5": 1
    }, 
    "coaches": [
        "yourcoach@gmail.com"
    ], 
    "joined": "2011-02-04T06:01:47Z", 
    "kind": "UserData", 
    "last_activity": "2011-05-04T06:01:47Z", 
    "nickname": "Gob Bluth",
    "points": 9188, 
    "proficient_exercises": [
        "addition_1", 
        "subtraction_1", 
        "multiplication_0.5"
    ], 
    "suggested_exercises": [
        "addition_2", 
        "subtraction_2"
    ], 
    "total_seconds_watched": 105, 
    "user_id": "you@gmail.com", 
    "prettified_user_email": "you@gmail.com"    
} 
	 */
	
	public User() { }
	
	public User(String username) {
		this.nickname = username;
	}
	
	@DatabaseField
	String token;
	
	@DatabaseField
	String secret;
	
	@DatabaseField
	boolean isSignedIn;
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	static class BadgeCounts {
		int type0;
		int type1;
		int type2;
		int type3;
		int type4;
		int type5;
	}
	
	BadgeCounts badgeCounts;
	
	@DatabaseField
	String user_id;
	
	@DatabaseField
	String prettified_user_email;
	
	@DatabaseField
	int total_seconds_watched;
	
	@DatabaseField
	int points;
	
	@DatabaseField(id=true)
	String nickname;
	
	@DatabaseField
	Date joined;

	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @param token the token to set
	 */
	public void setToken(String token) {
		this.token = token;
	}

	/**
	 * @return the secret
	 */
	public String getSecret() {
		return secret;
	}

	/**
	 * @param secret the secret to set
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * @return the isSignedIn
	 */
	public boolean isSignedIn() {
		return isSignedIn;
	}

	/**
	 * @param isSignedIn the isSignedIn to set
	 */
	public void setSignedIn(boolean isSignedIn) {
		this.isSignedIn = isSignedIn;
	}

	/**
	 * @return the badgeCounts
	 */
	public BadgeCounts getBadgeCounts() {
		return badgeCounts;
	}

	/**
	 * @param badgeCounts the badgeCounts to set
	 */
	public void setBadgeCounts(BadgeCounts badgeCounts) {
		this.badgeCounts = badgeCounts;
	}

	/**
	 * @return the user_id
	 */
	public String getUser_id() {
		return user_id;
	}

	/**
	 * @param user_id the user_id to set
	 */
	public void setUser_id(String user_id) {
		this.user_id = user_id;
	}

	/**
	 * @return the prettified_user_email
	 */
	public String getPrettified_user_email() {
		return prettified_user_email;
	}

	/**
	 * @param prettified_user_email the prettified_user_email to set
	 */
	public void setPrettified_user_email(String prettified_user_email) {
		this.prettified_user_email = prettified_user_email;
	}

	/**
	 * @return the total_seconds_watched
	 */
	public int getTotal_seconds_watched() {
		return total_seconds_watched;
	}

	/**
	 * @param total_seconds_watched the total_seconds_watched to set
	 */
	public void setTotal_seconds_watched(int total_seconds_watched) {
		this.total_seconds_watched = total_seconds_watched;
	}

	/**
	 * @return the points
	 */
	public int getPoints() {
		return points;
	}

	/**
	 * @param points the points to set
	 */
	public void setPoints(int points) {
		this.points = points;
	}

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @param nickname the nickname to set
	 */
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	/**
	 * @return the joined
	 */
	public Date getJoined() {
		return joined;
	}

	/**
	 * @param joined the joined to set
	 */
	public void setJoined(Date joined) {
		this.joined = joined;
	}
}
