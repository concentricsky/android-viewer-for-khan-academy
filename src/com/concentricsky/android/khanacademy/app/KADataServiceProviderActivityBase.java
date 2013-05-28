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

import android.app.Activity;
import android.os.Bundle;

import com.concentricsky.android.khanacademy.data.KADataService;
import com.concentricsky.android.khanacademy.data.KADataService.ServiceUnavailableException;
import com.concentricsky.android.khanacademy.data.KADataServiceProviderImpl;
import com.concentricsky.android.khanacademy.util.ObjectCallback;

public abstract class KADataServiceProviderActivityBase extends Activity implements KADataService.Provider {

	public static final String LOG_TAG = KADataServiceProviderActivityBase.class.getSimpleName();
	
	private KADataServiceProviderImpl serviceProvider;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		serviceProvider = new KADataServiceProviderImpl(this);
	}
	
	@Override
	protected void onDestroy() {
		serviceProvider.destroy();
		serviceProvider = null;
		super.onDestroy();
	}
	
	// Implement KADataService.Provider
	@Override
	public KADataService getDataService() throws ServiceUnavailableException {
		return serviceProvider.getDataService();
	}

	@Override
	public boolean requestDataService(ObjectCallback<KADataService> callback) {
		return serviceProvider.requestDataService(callback);
	}

	@Override
	public boolean cancelDataServiceRequest(
			ObjectCallback<KADataService> callback) {
		return serviceProvider.cancelDataServiceRequest(callback);
	}
	
}
