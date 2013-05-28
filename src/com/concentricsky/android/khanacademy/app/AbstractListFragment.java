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
package com.concentricsky.android.khanacademy.app;

import static com.concentricsky.android.khanacademy.Constants.PARAM_TOPIC_ID;

import java.sql.SQLException;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.concentricsky.android.khanacademy.Constants;
import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.KADataService.ServiceUnavailableException;
import com.concentricsky.android.khanacademy.data.db.DatabaseHelper;
import com.concentricsky.android.khanacademy.data.db.EntityBase;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.concentricsky.android.khanacademy.util.Log;
import com.concentricsky.android.khanacademy.util.ObjectCallback;
import com.j256.ormlite.android.AndroidDatabaseResults;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

public abstract class AbstractListFragment<T extends EntityBase> extends android.app.ListFragment
		implements ObjectCallback<KADataService> {
	
	public static final String LOG_TAG = AbstractListFragment.class.getSimpleName();
	public static final int LIST_VIEW_ID = 123890;
	
	protected Dao<T, String> dao;
	
	private Callbacks callbacks;
	private String topicId;
	private boolean isShowingDownloadedVideosOnly;
	private Topic topic;
	private Cursor topicCursor;
	
	protected Callbacks getCallbacks() {
		return callbacks;
	}
	
	
	// ABSTRACT
	protected abstract ListAdapter buildListAdapter();
	protected abstract Class<T> getEntityClass();

	public interface Callbacks extends KADataService.Provider {
		public void onRefreshRequested();
	}
	
	
	// CONSTRUCTORS
	
	public AbstractListFragment() {
		super();
	}
	
	// LIFECYCLE
		
	/**
	 * Build the bundle that will be passed to a future onCreate call.
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.v(LOG_TAG, ".onSaveInstanceState");
		
		outState.putString(PARAM_TOPIC_ID, topicId);
	}

	/**
	 * Load state from savedInstanceState / shared preferences.
	 * 
	 * Cleanup in onDestroy.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(LOG_TAG, ".onCreate");
			
		// Ensure that the Activity implements the correct Callbacks interface.
		@SuppressWarnings("rawtypes")
		Class<? extends AbstractListFragment> cls = getClass();
		Activity a = getActivity();
		boolean okay = false;
		for (Class<?> c : cls.getDeclaredClasses()) {
			if (Callbacks.class.isAssignableFrom(c) && c.isInstance(a)) {
				okay = true;
			}
		}
		if (!okay) {
			throw new IllegalStateException(String.format("Activity must implement %s.Callbacks", cls.getSimpleName()));
		}
		callbacks = (Callbacks) getActivity();
	
		// Set / restore state.
		topicId = null;
		if (savedInstanceState != null && savedInstanceState.containsKey(PARAM_TOPIC_ID)) {
			String id = savedInstanceState.getString(PARAM_TOPIC_ID);
			if (id != null) {
				topicId = id;
			}
		} else {
			Bundle args = getArguments();
			if (args != null && args.containsKey(PARAM_TOPIC_ID)) {
				String id = args.getString(PARAM_TOPIC_ID);
				if (id != null) {
					topicId = id;
				}
			}
		}
		
		isShowingDownloadedVideosOnly = getActivity().getSharedPreferences(
				Constants.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(Constants.SETTING_SHOW_DL_ONLY, false);
		
	}
	
	/**
	 * Counterpart to onCreate.
	 */
	@Override
	public void onDestroy() {
		Log.v(LOG_TAG, ".onDestroy");
		callbacks = null;
		super.onDestroy();
	}
	
	/**
	 * Build adapter, attach to data service, get appropriate cursor, and reset the list.
	 * 
	 * Cleanup in onDestroyView.
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Log.v(LOG_TAG, ".onActivityCreated");
		super.onActivityCreated(savedInstanceState);
		
        ListAdapter adapter = buildListAdapter();
        setListAdapter(adapter);

		Activity a = getActivity();
		try {
			KADataService dataService = ((KADataService.Provider) a).getDataService();
			this.call(dataService);
			Log.d(LOG_TAG, "Service already available.");
		} catch (ServiceUnavailableException e) {
			boolean serviceExpected = ((KADataService.Provider) a).requestDataService(this);
			Log.d(LOG_TAG, String.format("Service expected: %b.", serviceExpected));
		}
		
		getListView().setOverScrollMode(ListView.OVER_SCROLL_ALWAYS);
	}
	
	/**
	 * Counterpart to onCreateView. Since onActivityCreated has no counterpart,
	 * do those things here also.
	 */
	@Override
	public void onDestroyView() {
		Log.d(LOG_TAG, ".onDestroyView");
		if (topicCursor != null) {
			// This is opened in onActivityCreated.
			topicCursor.close();
		}
		setListAdapter(null);
		((KADataService.Provider) getActivity()).cancelDataServiceRequest(this);
		super.onDestroyView();
	}
	
	// CALLBACKS
	
    /**
     * implements ObjectCallback<KADataService>
     */
	@Override
	public void call(KADataService service) {
		// Called when the service becomes available.
		
		if (topicId != null) {
	        DatabaseHelper dbh = service.getHelper();
			try {
				Dao<Topic, String> topicDao = dbh.getTopicDao();
				topic = topicDao.queryForId(topicId);
				dao = dbh.getDao(getEntityClass());
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		else {
			topic = service.getRootTopic();
			topicId = topic.getId();
		}

		resetListContents(topicId);
	}
	
	
	// PRIVATE
	
	private void resetListContents(String topicId) {
    	Log.d(LOG_TAG, "resetListContents");
    	
    	AndroidDatabaseResults iterator = null;
    	QueryBuilder<T, String> qb = this.dao.queryBuilder();
    	qb = qb.orderBy("seq", true);
    	try {
    		Where<T, String> where = qb.where();
	    	where.eq("parentTopic_id", topicId);
	    	addToQuery(where);
	    	PreparedQuery<T> pq = qb.prepare();
	    	iterator = (AndroidDatabaseResults) dao.iterator(pq).getRawResults();
	    	if (topicCursor != null) {
	    		topicCursor.close();
	    	}
	    	topicCursor = iterator.getRawCursor();
		} catch (SQLException e) {
			e.printStackTrace();
		}
    	
    	CursorAdapter adapter = (CursorAdapter) getListAdapter();
    	adapter.changeCursor(topicCursor);
    	adapter.notifyDataSetChanged();
	}
    
	// PUBLIC

    public void setActivateOnItemClick(boolean activateOnItemClick) {
        getListView().setChoiceMode(activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);
    }
    
    public String getTitle() {
    	// TODO : special case the root
    	return topic.getTitle();
    }
    
    public Topic getTopic() {
    	return topic;
    }
    
    /**
     * Whether to show all videos, or just the downloaded ones.
     * 
     * There is no setter for this, as a change will just cause a new fragment to be created.
     * 
     * @return true if the fragment should display only the videos with local copies, false otherwise.
     */
    protected boolean isShowingDownloadedVideosOnly() {
    	return isShowingDownloadedVideosOnly;
    }
    
    /**
     * Make any needed modifications to the query just before it is executed and its cursor passed to the list adapter.
     * 
     * Default implementation does nothing; subclasses override to customize.
     * 
     * @param query The query to modify.
     * @return The same query, after modifying it.
     * @throws SQLException
     */
    protected Where<T, String> addToQuery(Where<T, String> where) throws SQLException {
    	return where;
    }
    
}
