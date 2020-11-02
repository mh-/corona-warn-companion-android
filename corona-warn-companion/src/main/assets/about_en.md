© 2020  Michael Huebler and other contributors.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

# Features
This app helps you to better understand warnings of the official Corona-Warn-App.

**ATTENTION:** FOR FULL FUNCTIONALITY, THE APP REQUIRES ROOT PERMISSIONS. Without root permissions, the app cannot access your risk encounters, and only three features are available: 1. Test how many diagnosis keys can be downloaded from the server; 2. Use RaMBLE data; 3. Demo Mode.

### What the app does:
1. The app reads the Rolling Proximity IDs recorded by your device from the Exposure Notifications database (this is only possible with root permissions, which is why official Exposure Notifications apps, such as Corona-Warn-App, cannot display these details).

   ![-Example Recorded Encounters-](file:///android_asset/rpis_en.png)
   Alternatively, the app can also read a database exported from RaMBLE (does not need root permissions).

2. The app downloads the Diagnosis Keys from the official German Corona-Warn-Server and other countries' servers, as selected by you. For Germany, it downloads the keys published daily for the last few days, and the keys published every hour for today. Therefore, different information than in the official app might be displayed.

   ![-Example Diagnosis Keys-](file:///android_asset/dks_en.png)

3. The app compares both in order to find matches (risk encounters).

   ![-Example Matches-](file:///android_asset/matches_en.png)

If risk encounters are found, it shows the details:
At which times and with which radio attenuation (roughly corresponds to the distance) did the encounters take place, and what level of transmission risk did the encounters have.

![-Example Details-](file:///android_asset/details_en.png)

Note that 1 means a low and 8 means a high transmission risk.

### What the app does not do:
- The app does not process any personal data.
- The app only accesses the internet for the purpose 2 (see above), i.e. it only downloads data from the official warning servers and does not send any data to other servers.
- The app does not show any advertising.

**Note:** This app has only been tested on a few devices so far, so it is possible that it will not work and/or show incorrect results.

# Open Source
The source code of the app is published at https://github.com/mh-/corona-warn-companion-android, so you can check the source code, build the app yourself, and you are also welcome to contribute to improvements.

# Countries covered
- Austria
- Belgium
- Canada
- Czechia
- Germany, Ireland, Italy
- Netherlands
- Poland
- Switzerland
- England and Wales

In the top right hand corner of the app is a menu button, please use this to select the countries from which you want to download Diagnosis Keys.

Please note that we mainly follow changes that happen in the German CWA setup; if you experience problems with another country, please let us know via a GitHub issue.

# Other
- The app is used for private purposes only, it is not used for any business purposes.
- The app is not a "hacking tool". It only reads data from the memory of your own device, which is stored there without additional encryption.
