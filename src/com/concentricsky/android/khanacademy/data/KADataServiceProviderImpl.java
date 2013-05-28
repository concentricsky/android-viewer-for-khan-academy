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
package com.concentricsky.android.khanacademy.data;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.concentricsky.android.khanacademy.data.KADataService.KADataBinder;
import com.concentricsky.android.khanacademy.data.KADataService.Provider;
import com.concentricsky.android.khanacademy.data.KADataService.ServiceUnavailableException;
import com.concentricsky.android.khanacademy.util.ObjectCallback;

public class KADataServiceProviderImpl implements Provider {

    private Activity activity;
    private KADataService dataService;
    private List<ObjectCallback<KADataService>> dataServiceCallbacks = new ArrayList<ObjectCallback<KADataService>>();
    private boolean dataServiceExpected;
    private boolean serviceRequested;
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // NOTE : we ignore the name, as we only connect to a single type of dataService.
            dataService = ((KADataBinder) service).getService();
            for (ObjectCallback<KADataService> callback : dataServiceCallbacks) {
                callback.call(dataService);
            }
            dataServiceCallbacks.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // NOTE : we ignore the name, as we only connect to a single type of dataService.
            dataService = null;
        }
        
    };
    
    public void destroy() {
        dataServiceCallbacks.clear();
        if (serviceRequested) {
	        activity.unbindService(serviceConnection);
        }
        activity = null;
        serviceConnection = null;
        dataService = null;
    }

    public KADataServiceProviderImpl(Activity activity) {
        this.activity = activity;
        serviceRequested = false;

    }

    @Override
    public KADataService getDataService() throws ServiceUnavailableException {
        if (dataService == null) {
            throw new ServiceUnavailableException(dataServiceExpected);
        }
        return dataService;
    }

    /**
     * Accept a callback for when the data dataService is available.
     * 
     * Call the callback immediately if already available, else enqueue it.
     * 
     * @return true if we expect a data dataService to be available in the future, false otherwise.
     */
    @Override
    public boolean requestDataService(ObjectCallback<KADataService> callback) {
        if (dataService != null) {
            callback.call(dataService);
        } else {
            dataServiceCallbacks.add(callback);
        }
        if (!serviceRequested) {
	        // Get a binding to the data dataService.
	        Intent serviceIntent = new Intent(activity, KADataService.class);
	        dataServiceExpected = activity.bindService(serviceIntent, serviceConnection, Service.BIND_AUTO_CREATE);
        	serviceRequested = true;
        }
        return dataServiceExpected;
    }
    
    /**
     * Unregister a previously registered callback.
     * 
     * @return true if the provided callback existed and was successfully unregistered, false otherwise.
     */
    @Override
    public boolean cancelDataServiceRequest(ObjectCallback<KADataService> callback) {
        return dataServiceCallbacks.remove(callback);
    }

}

