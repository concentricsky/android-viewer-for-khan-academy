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

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to handle logging.
 * 
 * Filters log messages by tag, and forwards only those with tags 
 * enabled at the appropriate level to the respective android.util.Log 
 * functions. To enable a tag, just call Log.enable(tag, level) before 
 * sending messages with that tag.
 * 
 * For example, to enable log messages of WARN level or higher for KAResponseCache,
 * call Log.enable(KAResponseCache.LOG_TAG, Log.WARN).
 * 
 * By default, all logging is disabled.
 * 
 * @author austinlally
 *
 */
public class Log {
	
	public static final String LOG_TAG = "KA";
	
	public static final int VERBOSE = 0;
	public static final int DEBUG = 1;
	public static final int INFO = 2;
	public static final int WARN = 3;
	public static final int ERROR = 4;
	public static final int ASSERT = 5;
	public static final int SUPPRESS = 6;
	
	private static Map<String, Integer> enabledTags = new HashMap<String, Integer> ();
	private static int defaultLogLevel = SUPPRESS;
	
	/**
	 * Set the default logging level.  If no level has been set for a given tag, this
	 * default level will determine whether the message is logged. 
	 * 
	 * @param defaultLevel The default log level.
	 */
	public static void setDefaultLevel(int defaultLevel) {
		defaultLogLevel = defaultLevel;
	}
	
	/**
	 * Get the default log level, which is used to determine whether a message is logged
	 * if no level has been explicitly set for its tag.
	 * 
	 * @return The default log level.
	 */
	public static int getDefaultLevel() {
		return defaultLogLevel;
	}
	
	/**
	 * Get the log level for a given tag.
	 * 
	 * @param tag The tag for which to return a log level.
	 * @return The minimum level at which logs will be posted for the given tag.
	 */
	public static int getLevel(String tag) {
		Integer level = enabledTags.get(tag);
		if (level == null) return SUPPRESS;
		return level;
	}
	
	/**
	 * Enable a tag for future log messages.
	 * 
	 * @param tag The tag to enable, or "*" to set the default.
	 * @param level The minimum level to log.
	 */
	public static void setLevel(String tag, int level) {
		enabledTags.put(tag, level);
	}
	
	/**
	 * Disable a tag for future log messages.
	 * 
	 * @param tag The tag to disable, or "*" to suppress logging by default.
	 */
	public static void disable(String tag) { 
		enabledTags.remove(tag);
	}
	
	/**
	 * Forward a message to the android logger.  
	 * 
	 * The message will be forwarded only if its tag has previously been enabled at the 
	 * DEBUG or lower level, or if the default log level is DEBUG or lower. 
	 * 
	 * The tag is wrapped into the message, and sent to the android 
	 * logger with the application's tag.  For a call like Log.d("a", "b") this results in 
	 * the call android.util.Log.d("KA", "a: b") if "a" is enabled at DEBUG or lower, and 
	 * no operation otherwise.
	 * 
	 * @param tag The tag to check against the enabled list and prepend to the message.
	 * @param msg The message to log.
	 */
	public static void d(String tag, String msg) {
		Integer level = enabledTags.get(tag);
		if ( (level != null && level <= DEBUG) || (level == null && defaultLogLevel <= DEBUG) ) {
			android.util.Log.d(LOG_TAG, tag + ": " + msg);
		}
	}
	
	/**
	 * Forward a message to the android logger.  
	 * 
	 * The message will be forwarded only if its tag has previously been enabled at the 
	 * ERROR or lower level, or if the default log level is ERROR or lower. 
	 * 
	 * The tag is wrapped into the message, and sent to the android 
	 * logger with the application's tag.  For a call like Log.e("a", "b") this results in 
	 * the call android.util.Log.e("KA", "a: b") if "a" is enabled at ERROR or lower, and 
	 * no operation otherwise.
	 * 
	 * @param tag The tag to check against the enabled list and prepend to the message.
	 * @param msg The message to log.
	 */
	public static void e(String tag, String msg) {
		Integer level = enabledTags.get(tag);
		if ( (level != null && level <= ERROR) || (level == null && defaultLogLevel <= ERROR) ) {
			android.util.Log.e(LOG_TAG, tag + ": " + msg);
		}
	}
	
	/**
	 * Forward a message to the android logger.  
	 * 
	 * The message will be forwarded only if its tag has previously been enabled at the 
	 * WARN or lower level, or if the default log level is WARN or lower. 
	 * 
	 * The tag is wrapped into the message, and sent to the android 
	 * logger with the application's tag.  For a call like Log.w("a", "b") this results in 
	 * the call android.util.Log.w("KA", "a: b") if "a" is enabled at WARN or lower, and 
	 * no operation otherwise.
	 * 
	 * @param tag The tag to check against the enabled list and prepend to the message.
	 * @param msg The message to log.
	 */
	public static void w(String tag, String msg) {
		Integer level = enabledTags.get(tag);
		if ( (level != null && level <= WARN) || (level == null && defaultLogLevel <= WARN) ) {
			android.util.Log.w(LOG_TAG, tag + ": " + msg);
		}
	}
	
	/**
	 * Forward a message to the android logger.  
	 * 
	 * The message will be forwarded only if its tag has previously been enabled at the 
	 * INFO or lower level, or if the default log level is INFO or lower. 
	 * 
	 * The tag is wrapped into the message, and sent to the android 
	 * logger with the application's tag.  For a call like Log.i("a", "b") this results in 
	 * the call android.util.Log.i("KA", "a: b") if "a" is enabled at INFO or lower, and 
	 * no operation otherwise.
	 * 
	 * @param tag The tag to check against the enabled list and prepend to the message.
	 * @param msg The message to log.
	 */
	public static void i(String tag, String msg) {
		Integer level = enabledTags.get(tag);
		if ( (level != null && level <= INFO) || (level == null && defaultLogLevel <= INFO) ) {
			android.util.Log.i(LOG_TAG, tag + ": " + msg);
		}
	}
	
	/**
	 * Forward a message to the android logger.  
	 * 
	 * The message will be forwarded only if its tag has previously been enabled at the 
	 * VERBOSE level, or if the default log level is VERBOSE. 
	 * 
	 * The tag is wrapped into the message, and sent to the android 
	 * logger with the application's tag.  For a call like Log.v("a", "b") this results in 
	 * the call android.util.Log.v("KA", "a: b") if "a" is enabled at VERBOSE, and 
	 * no operation otherwise.
	 * 
	 * @param tag The tag to check against the enabled list and prepend to the message.
	 * @param msg The message to log.
	 */
	public static void v(String tag, String msg) {
		Integer level = enabledTags.get(tag);
		if ( (level != null && level <= VERBOSE) || (level == null && defaultLogLevel <= VERBOSE) ) {
			android.util.Log.v(LOG_TAG, tag + ": " + msg);
		}
	}
	
	/**
	 * Convenience function for passing in the level at runtime.
	 */
	public static void log(int priority, String tag, String msg) {
		switch (priority) {
		case VERBOSE:
			v(tag, msg); break;
		case INFO:
			i(tag, msg); break;
		case DEBUG:
			d(tag, msg); break;
		case WARN:
			w(tag, msg); break;
		case ERROR:
			e(tag, msg); break;
		case ASSERT:
		case SUPPRESS:
		default:
			// nop
		}
	
	}
	
}
