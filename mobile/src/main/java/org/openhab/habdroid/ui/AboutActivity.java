package org.openhab.habdroid.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

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
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.ServerProperties;
import org.openhab.habdroid.util.SyncHttpClient;
import org.openhab.habdroid.util.Util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static org.openhab.habdroid.util.Util.obfuscateString;

public class AboutActivity extends AppCompatActivity implements
        FragmentManager.OnBackStackChangedListener{
    private final static String TAG = AboutActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

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

        updateTitle();
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

    @Override
    public void onBackStackChanged() {
        updateTitle();
    }

    private void updateTitle() {
        FragmentManager fm = getSupportFragmentManager();
        int count = fm.getBackStackEntryCount();
        @StringRes int titleResId = count > 0
                ? fm.getBackStackEntryAt(count - 1).getBreadCrumbTitleRes()
                : R.string.about_title;
        setTitle(titleResId);
    }

    public static class AboutMainFragment extends MaterialAboutFragment {
        private final static String TAG = AboutMainFragment.class.getSimpleName();
        private final static String URL_TO_GITHUB = "https://github.com/openhab/openhab-android";
        private ServerProperties mServerProperties;
        private Connection mConnection;

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            mServerProperties = getArguments().getParcelable("serverProperties");
            try {
                mConnection = ConnectionFactory.getUsableConnection();
            } catch (ConnectionException ignored) {}
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @Override
        protected MaterialAboutList getMaterialAboutList(final Context context) {
            String year = new SimpleDateFormat("yyyy", Locale.US)
                    .format(Calendar.getInstance().getTime());

            MaterialAboutCard.Builder appCard = new MaterialAboutCard.Builder();
            appCard.addItem(new MaterialAboutTitleItem.Builder()
                    .text(R.string.app_name)
                    .desc(context.getString(R.string.about_copyright, year))
                    .icon(R.mipmap.icon)
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.version)
                    .subText(context.getString(R.string.about_version_string,
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
                    .icon(R.drawable.ic_github_grey_24dp)
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
                    .setOnClickAction(new MaterialAboutItemOnClickAction() {
                        @Override
                        public void onClick() {
                            Fragment f = new LibsBuilder()
                                    .withFields(R.string.class.getFields())
                                    .withLicenseShown(true)
                                    .withAutoDetect(true)
                                    .supportFragment();
                            getFragmentManager()
                                    .beginTransaction()
                                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                            R.anim.slide_in_left, R.anim.slide_out_right)
                                    .replace(R.id.about_container, f)
                                    .setBreadCrumbTitle(R.string.title_activity_libraries)
                                    .addToBackStack(null)
                                    .commit();
                        }
                    })
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_privacy_policy)
                    .icon(R.drawable.ic_security_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://www.openhabfoundation.org/privacy"))
                    .build());

            MaterialAboutCard.Builder ohServerCard = new MaterialAboutCard.Builder();
            ohServerCard.title(R.string.about_server);
            if (mConnection == null || mServerProperties == null) {
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.error_about_no_conn)
                        .icon(R.drawable.ic_info_outline)
                        .build());
            } else {
                String apiVersion = getApiVersion();
                if (TextUtils.isEmpty(apiVersion)) {
                    apiVersion = context.getString(R.string.unknown);
                }
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_apiversion_label)
                        .subText(apiVersion)
                        .icon(R.drawable.ic_info_outline)
                        .build());

                String uuid = getServerUuid();
                if (TextUtils.isEmpty(uuid)) {
                    uuid = context.getString(R.string.unknown);
                }
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_uuid_label)
                        .subText(uuid)
                        .icon(R.drawable.ic_info_outline)
                        .build());

                if (!useJsonApi()) {
                    String secret = getServerSecret();
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
                    .text(R.string.info_openhab_push_notification_label)
                    .subText(CloudMessagingHelper.getPushNotificationStatusResId())
                    .icon(R.drawable.ic_info_outline)
                    .build());

            MaterialAboutCard.Builder ohCommunityCard = new MaterialAboutCard.Builder();
            ohCommunityCard.title(R.string.about_community);
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_docs)
                    .icon(R.drawable.ic_collections_bookmark_grey_24dp)
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://www.openhab.org/docs/"))
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
                    .setOnClickAction(MaterialAboutItemOnClickRedirect("https://www.openhabfoundation.org/"))
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

        private String getServerSecret() {
            SyncHttpClient.HttpTextResult result =
                    mConnection.getSyncHttpClient().get("static/secret").asText();
            if (result.isSuccessful()) {
                Log.d(TAG, "Got secret " + obfuscateString(result.response));
                return result.response;
            } else {
                Log.e(TAG, "Could not fetch server secret " + result.error);
                return null;
            }
        }

        private boolean useJsonApi() {
            return mServerProperties != null && mServerProperties.hasJsonApi();
        }

        private String getServerUuid() {
            final String uuidUrl = useJsonApi() ? "rest/uuid" : "static/uuid";
            SyncHttpClient.HttpTextResult result =
                    mConnection.getSyncHttpClient().get(uuidUrl).asText();
            if (result.isSuccessful()) {
                Log.d(TAG, "Got uuid " + obfuscateString(result.response));
                return result.response;
            } else {
                Log.e(TAG, "Could not fetch server uuid " + result.error);
                return null;
            }
        }

        private String getApiVersion() {
            String versionUrl = useJsonApi() ? "rest" : "static/version";
            Log.d(TAG, "url = " + versionUrl);
            SyncHttpClient.HttpTextResult result =
                    mConnection.getSyncHttpClient().get(versionUrl).asText();
            if (!result.isSuccessful()) {
                Log.e(TAG, "Could not fetch rest API version " + result.error);
            } else {
                if (!useJsonApi()) {
                    return result.response;
                } else {
                    try {
                        JSONObject pageJson = new JSONObject(result.response);
                        return pageJson.getString("version");
                    } catch (JSONException e) {
                        Log.e(TAG, "Problem fetching version string", e);
                    }
                }
            }
            return null;
        }
    }
}
