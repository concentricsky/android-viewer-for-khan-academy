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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;

public class DatabaseExporter {
	
	public static final String LOG_TAG = DatabaseExporter.class.getSimpleName();
	
	private final Context context;
	
	public DatabaseExporter(Context context) {
		this.context = context;
	}
	
	public void export(String dbname) {
		Log.d(LOG_TAG, "export: " + dbname);
		// copy the db to somewhere I can get it
		File infile = context.getDatabasePath(dbname);
		File outfile = new File(context.getExternalFilesDir(null), dbname);
		
		try {
			FileInputStream in = new FileInputStream(infile);
			FileOutputStream out = new FileOutputStream(outfile);
			copyFile(in, out);
			Log.i(LOG_TAG, "database exported to: " + outfile);
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(LOG_TAG, "database export failed");
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
