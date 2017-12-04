package org.openhab.habdroid.ui;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

public class RemoteConnectionSettingsFragment extends OpenHABPreferencesActivity.SettingsFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.settings_openhab_alt_connection);
    }

    @Override
    protected void updateAndInitPreferences() {
        addPreferencesFromResource(R.xml.remote_connection_preferences);

        initEditorPreference(Constants.PREFERENCE_ALTURL, R.string.settings_openhab_alturl_summary, false);
        initEditorPreference(Constants.PREFERENCE_REMOTE_USERNAME, 0, false);
        initEditorPreference(Constants.PREFERENCE_REMOTE_PASSWORD, 0, true);
    }
}
