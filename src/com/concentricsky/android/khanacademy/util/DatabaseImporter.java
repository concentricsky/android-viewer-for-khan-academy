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
package com.concentricsky.android.khanacademy.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;

public class DatabaseImporter {
	
	public static final String LOG_TAG = DatabaseImporter.class.getSimpleName();
	
	private final Context context;
	
	public DatabaseImporter(Context context) {
		this.context = context;
	}
	
	public void import_(int resourceId, String dbname) {
		Log.d(LOG_TAG, "import_: " + dbname);
		// copy the db from raw resources to where it belongs
		File outfile = context.getDatabasePath(dbname);
		Log.d(LOG_TAG, "   db path: " + outfile);
		File dbdir = new File(outfile.getParent());
		Log.d(LOG_TAG, "   db dir: " + dbdir);
		dbdir.mkdirs();
		
		InputStream in = null;
		FileOutputStream out = null;
		try {
			in = context.getResources().openRawResource(resourceId);
			out = new FileOutputStream(outfile);
			copyFile(in, out);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) { }
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) { }
			}
		}
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException {
	    byte[] buffer = new byte[1024];
	    int read;
	    while((read = in.read(buffer)) != -1){
	      out.write(buffer, 0, read);
	    }
	}

}
