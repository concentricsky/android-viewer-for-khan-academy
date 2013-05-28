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

public interface EntityVisitor {
	public void visit(Topic topic);
	public void visit(Video video);
	public void visit(EntityBase.Impl entity);
}