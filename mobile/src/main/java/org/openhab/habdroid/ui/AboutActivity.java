package org.openhab.habdroid.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import com.danielstone.materialaboutlibrary.MaterialAboutFragment;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.mikepenz.aboutlibraries.LibsBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.Util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Headers;

import static org.openhab.habdroid.util.Util.obfuscateString;

public class AboutActivity extends AppCompatActivity {
    private final static String TAG = AboutActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            AboutMainFragment f = new AboutMainFragment();
            f.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.about_container, f)
                    .commit();
        }


        setResult(RESULT_OK);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }

    public static class AboutMainFragment extends MaterialAboutFragment {
        private final static String TAG = AboutMainFragment.class.getSimpleName();
        private final static String URL_TO_GITHUB = "https://github.com/openhab/openhab-android";
        private int mOpenHABVersion = 0;
        private Connection conn;

        @Override
        public void onResume() {
            super.onResume();
            try {
                conn = ConnectionFactory.getUsableConnection();
            } catch (ConnectionException ignored) {}
        }

        @Override
        protected MaterialAboutList getMaterialAboutList(final Context context) {
            mOpenHABVersion = getArguments().getInt("openHABVersion", 0);

            String year = new SimpleDateFormat("yyyy", Locale.US)
                    .format(Calendar.getInstance().getTime());

            MaterialAboutCard.Builder appCard = new MaterialAboutCard.Builder();
            appCard.addItem(new MaterialAboutTitleItem.Builder()
                    .text(R.string.app_name)
                    .desc(String.format(getString(R.string.about_copyright),year))
                    .icon(R.mipmap.icon)
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.version)
                    .subText(getString(R.string.about_version_string,
                            BuildConfig.VERSION_NAME,
                            DateFormat.getDateTimeInstance().format(BuildConfig.buildTime)))
                    .icon(R.drawable.ic_update_grey_24dp)
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_changelog)
                    .icon(R.drawable.ic_track_changes_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect(URL_TO_GITHUB + "/releases"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_source_code)
                    .icon(R.drawable.ic_github)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect(URL_TO_GITHUB))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_issues)
                    .icon(R.drawable.ic_bug_report_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect(URL_TO_GITHUB + "/issues"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_license_title)
                    .subText(R.string.about_license)
                    .icon(R.drawable.ic_account_balance_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect(URL_TO_GITHUB + "/blob/master/LICENSE"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.title_activity_libraries)
                    .icon(R.drawable.ic_developer_mode_grey_24dp)
                    .setOnClickAction(() -> new LibsBuilder()
                            .withActivityTheme(getTheme())
                            .withFields(R.string.class.getFields())
                            .withLicenseShown(true)
                            .withAutoDetect(true)
                            .start(context))
                    .build());

            MaterialAboutCard.Builder ohServerCard = new MaterialAboutCard.Builder();
            ohServerCard.title(R.string.about_server);
            if(conn == null || mOpenHABVersion == 0) {
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.error_about_no_conn)
                        .icon(R.drawable.ic_info_outline)
                        .build());
            } else {
                String apiVersion = getApiVersion(conn);
                if(TextUtils.isEmpty(apiVersion)) {
                    apiVersion = getString(R.string.unknown);
                }
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_apiversion_label)
                        .subText(apiVersion)
                        .icon(R.drawable.ic_info_outline)
                        .build());

                String uuid = getServerUuid(conn);
                if(TextUtils.isEmpty(uuid)) {
                    uuid = getString(R.string.unknown);
                }
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_uuid_label)
                        .subText(uuid)
                        .icon(R.drawable.ic_info_outline)
                        .build());

                if (mOpenHABVersion == 1) {
                    String secret = getServerSecret(conn);
                    if (!TextUtils.isEmpty(secret)) {
                        ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                                .text(R.string.info_openhab_secret_label)
                                .subText(secret)
                                .icon(R.drawable.ic_info_outline)
                                .build());
                    }
                }
            }
            ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.info_openhab_gcm_label)
                    .subText(getGcmText())
                    .icon(R.drawable.ic_info_outline)
                    .build());

            MaterialAboutCard.Builder ohCommunityCard = new MaterialAboutCard.Builder();
            ohCommunityCard.title(R.string.about_community);
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_docs)
                    .icon(R.drawable.ic_collections_bookmark_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://docs.openhab.org/"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_community_forum)
                    .icon(R.drawable.ic_forum_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://community.openhab.org/"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_translation)
                    .icon(R.drawable.ic_language_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://crowdin.com/profile/openhab-bot"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_foundation)
                    .icon(R.drawable.ic_people_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("http://www.openhabfoundation.org/"))
                    .build());

            return new MaterialAboutList.Builder()
                    .addCard(appCard.build())
                    .addCard(ohServerCard.build())
                    .addCard(ohCommunityCard.build())
                    .build();
        }

        @Override
        protected int getTheme() {
            return Util.getActivityThemeID(getActivity());
        }

        private MaterialAboutItemOnClickAction MaterialAboutItemOnClickRedirect(final String url) {
            return new MaterialAboutItemOnClickAction() {
                @Override
                public void onClick() {
                    Uri uri = Uri.parse(url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            };
        }

        private String getGcmText() {
            if (OpenHABMainActivity.GCM_SENDER_ID == null) {
                return getString(R.string.info_openhab_gcm_not_connected);
            } else {
                return getString(R.string.info_openhab_gcm_connected, OpenHABMainActivity.GCM_SENDER_ID);
            }
        }

        private String getServerSecret(Connection conn) {
            final String[] secret = {null};
            conn.getSyncHttpClient().get("/static/secret", new MyHttpClient.TextResponseHandler() {
                @Override
                public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                    Log.e(TAG, "Could not fetch server secret " + error.getMessage());
                }

                @Override
                public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                    Log.d(TAG, "Got secret " + obfuscateString(responseString));
                    secret[0] = responseString;
                }
            });
            return secret[0];
        }

        private String getServerUuid(Connection conn) {
            final String uuidUrl;
            final String[] uuid = new String[1];
            if (mOpenHABVersion == 1) {
                uuidUrl = "/static/uuid";
            } else {
                uuidUrl = "/rest/uuid";
            }
            conn.getSyncHttpClient().get(uuidUrl, new MyHttpClient.TextResponseHandler() {
                @Override
                public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                    Log.e(TAG, "Could not fetch server uuid " + error.getMessage());
                }

                @Override
                public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                    Log.d(TAG, "Got uuid " + obfuscateString(responseString));
                    uuid[0] = responseString;
                }
            });
            return uuid[0];
        }

        private String getApiVersion(Connection conn) {
            String versionUrl;
            final String[] version = new String[1];
            if (mOpenHABVersion == 1) {
                versionUrl = "/static/version";
            } else {
                versionUrl = "/rest";
            }
            Log.d(TAG, "url = " + versionUrl);
            conn.getSyncHttpClient().get(versionUrl, new MyHttpClient.TextResponseHandler() {
                @Override
                public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                    Log.e(TAG, "Could not fetch rest API version " + error.getMessage());
                }

                @Override
                public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                    if(mOpenHABVersion == 1) {
                        version[0] = responseString;
                    } else {
                        try {
                            JSONObject pageJson = new JSONObject(responseString);
                            version[0] = pageJson.getString("version");
                        } catch (JSONException e) {
                            Log.e(TAG, "Problem fetching version string", e);
                        }
                    }
                    Log.d(TAG, "Got version " + version[0]);
                }
            });
            return version[0];
        }
    }
}
