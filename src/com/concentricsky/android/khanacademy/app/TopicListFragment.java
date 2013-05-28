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

import java.sql.SQLException;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import com.concentricsky.android.khan.R;
import com.concentricsky.android.khanacademy.data.db.Topic;
import com.j256.ormlite.stmt.Where;

public class TopicListFragment extends AbstractListFragment<Topic> {
	
	public interface Callbacks extends AbstractListFragment.Callbacks {
		public void onTopicSelected(String readableId);
	}
	
	@Override
	protected Callbacks getCallbacks() {
		return (Callbacks) super.getCallbacks();
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	}
	
	@Override
	protected CursorAdapter buildListAdapter() {
		return new TopicAdapter(getActivity());
	}
	
	public class TopicAdapter extends SimpleCursorAdapter {
		
		public TopicAdapter(Context context) {
			super(context, R.layout.list_item_topic, null, new String[] {"title"}, new int[] {android.R.id.text1}, 0);
		}

		@Override
		public boolean areAllItemsEnabled() {
			return !TopicListFragment.this.isShowingDownloadedVideosOnly();
		}
		
		@Override
		public boolean isEnabled(int pos) {
			// currently, areAllItemsEnabled is always true.
//			if (areAllItemsEnabled()) {
				return true;
//			}
		}
		
		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			super.bindView(view, context, cursor);
			
			// Stretch list item height if there are too few to fill the content area.
			ListView listView = getListView();
			int listViewHeight = listView.getMeasuredHeight();
			int itemCount = cursor.getCount();
			int itemHeight = view.getMeasuredHeight();
			int dividerHeight = listView.getDividerHeight();
			int totalDividerHeight = (itemCount - 1) * dividerHeight;
			int targetTotalItemHeight = listViewHeight - totalDividerHeight;
			int totalItemHeight = itemCount * itemHeight;
			boolean weNeedToUpsize = totalItemHeight < targetTotalItemHeight;
			
			if (weNeedToUpsize) {
				int targetItemHeight = targetTotalItemHeight / itemCount;
				view.setMinimumHeight(targetItemHeight);
			}
		}
	}
	
	@Override
	protected Class<Topic> getEntityClass() {
		return Topic.class;
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Cursor cursor = (Cursor) getListView().getItemAtPosition(position);
		String readableId = cursor.getString(cursor.getColumnIndex("_id"));
		getCallbacks().onTopicSelected(readableId);
	}
	
	@Override
    protected Where<Topic, String> addToQuery(Where<Topic, String> where) throws SQLException {
		where = super.addToQuery(where);
		where.and().gt("video_count", 0).and().in("child_kind", Topic.CHILD_KIND_TOPIC, Topic.CHILD_KIND_VIDEO);
		return where;
	}
	
}
