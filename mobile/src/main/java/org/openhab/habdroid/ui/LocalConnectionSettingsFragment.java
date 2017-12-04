package org.openhab.habdroid.ui;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

public class LocalConnectionSettingsFragment extends OpenHABPreferencesActivity.SettingsFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.settings_openhab_connection);
    }

    @Override
    protected void updateAndInitPreferences() {
        addPreferencesFromResource(R.xml.local_connection_preferences);

        initEditorPreference(Constants.PREFERENCE_URL, R.string.settings_openhab_url_summary, false);
        initEditorPreference(Constants.PREFERENCE_LOCAL_USERNAME, 0, false);
        initEditorPreference(Constants.PREFERENCE_LOCAL_PASSWORD, 0, true);
    }
}
