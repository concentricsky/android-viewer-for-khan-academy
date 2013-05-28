# Viewer for Khan Academy

This repository contains the source code for the Viewer for Khan Academy Android app available from [Google Play](https://play.google.com/store/apps/details?id=com.concentricsky.android.khan) and the [Amazon Appstore](http://www.amazon.com/Viewer-Academy-Kindle-Tablet-Edition/dp/B00ARP009Y/).

Please see the [issues](https://github.com/concentricsky/android-viewer-for-khan-academy/issues) section
to report any bugs or feature requests and to see the list of known issues.

## License

####[GPL Version 3](http://www.gnu.org/licenses/gpl-3.0.html)

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

For the full license text, follow the link above or see the LICENSE file.

## Building

##### The Basics

The build requires [Maven](http://maven.apache.org/download.html)
v3.0.3+ and the [Android SDK](http://developer.android.com/sdk/index.html)
to be installed in your development environment. In addition you'll need to set
the `ANDROID_HOME` environment variable to the location of your SDK:

    export ANDROID_HOME=/Users/austinlally/opt/android/sdk
    
##### API Credentials
    
Khan Academy API credentials are stored outside the repo for security reasons, so copy over the sample file:

    cp oauth_credentials.json.sample res/raw/oauth_credentials.json

Without a valid API key, most of the app will still run fine. When trying to log in,
though, you'll be presented with a blank page and the text "OAuth error. Invalid consumer."
To use user login features, register at https://www.khanacademy.org/api-apps/register and
enter the appropriate values into `oauth_credentials.json`.

With Maven installed and configured and `oauth_credentials.json` in place, build as follows:

* Run `mvn clean install` from the repository root directory to build the APK.
* Run `mvn clean install android:deploy android:run` to install and run the app also.

**NOTE**: If you already have the app installed through the Play or Amazon store, you'll need to remove it before installing a custom built version.

##### Content

There is a small python script to download the Khan Academy topic tree and build a database. We ship the application with a pre-built database to avoid a long initial startup time.

You'll want [pip](https://pypi.python.org/pypi/pip) and [virtualenv](https://pypi.python.org/pypi/virtualenv).

Initial setup:

    cd etc/scripts
    virtualenv --distribute env
    source env/bin/activate
    pip install -r requirements.txt

Each time you want to build a db:

	cd etc/scripts
    python build_db.py
    
To build the new db with the app, copy the output of this script to `res/raw/db`.

## Acknowledgements

This project relies on the following other free software:

* [android-maven-plugin](https://github.com/jayway/maven-android-plugin) for build automation
* [Jackson JSON](http://wiki.fasterxml.com/JacksonHome) for JSON parsing and serialization
* [OrmLite](http://ormlite.com/) for better database management
* [oauth-signpost](https://code.google.com/p/oauth-signpost/) to ease the pain of authentication
* [Spring for Android's RestTemplate](http://www.springsource.org/spring-android) for consuming REST services
* Jake Wharton's [DiskLruCache](https://github.com/JakeWharton/DiskLruCache) for thumbnail caching

## Contributing

Please fork this repository and contribute back using
[pull requests](https://github.com/concentricsky/android-viewer-for-khan-academy/pulls).

Any contributions, large or small, are welcomed and appreciated and will be thoroughly reviewed and discussed.
