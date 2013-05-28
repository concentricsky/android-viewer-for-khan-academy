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

import com.concentricsky.android.khanacademy.data.db.EntityBase;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.data.db.Video;

/**
 *   Updates the visited object with any values from `updateFrom' which are not the defaults.
 *   
 *   This isn't really the perfect approach, as this doesn't allow for updating a
 *   field TO its default value.
 *   
 *   However, the whole goal is to update existing objects that have had non-api
 *   values added with partial objects received from the api.
 *   
 *   Subclasses will override this and update their added fields.
 *   
 */
public abstract class BaseEntityUpdateVisitor<T extends EntityBase> implements EntityVisitor {
	private T updateFrom;
	public BaseEntityUpdateVisitor(T updateFrom) {
		this.updateFrom = updateFrom;
	}
	@Override
	public void visit(Topic topic) {
		baseUpdate(topic);
	}
	@Override
	public void visit(Video video) {
		baseUpdate(video);
	}
	@Override
	public void visit(EntityBase.Impl entity) {
		baseUpdate(entity);
	}
	private void baseUpdate(EntityBase toUpdate) {
		String value = updateFrom.getTitle();
		if (!isDefaultValue(value, String.class)) {
			toUpdate.setTitle(value);
		}
		
		value = updateFrom.getDescription();
		if (!isDefaultValue(value, String.class)) {
			toUpdate.setDescription(value);
		}
		
		value = updateFrom.getHide();
		if (!isDefaultValue(value, String.class)) {
			toUpdate.setHide(value);
		}
		
		value = updateFrom.getKa_url();
		if (!isDefaultValue(value, String.class)) {
			toUpdate.setKa_url(value);
		}
		
		Topic parent = updateFrom.getParentTopic();
		if (!isDefaultValue(parent, Topic.class)) {
			toUpdate.setParentTopic(parent);
		}
		
	}
	
	/**
	 * Test a value to see if it is the default value for its type.
	 * 
	 * @param value The value to check.
	 * @param valueType The value's class. In case of primitives, pass the wrapper class, such as Integer.
	 * @return true if the value is default for fields of the given type on this class, false otherwise.
	 */
	protected boolean isDefaultValue(Object value, Class<?> valueType) {
		
		if (String.class.equals(valueType)) {
			return null == value || "".equals(value);
		} else if (Integer.class.equals(valueType)) {
			return Integer.valueOf(0).equals(value);
		} else if (Topic.class.equals(valueType)) {
			return null == value;
		}
		
		throw new UnsupportedOperationException(String.format("Unknown type: %s", valueType.getSimpleName()));
	}
	
}