package org.openhab.habdroid.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.danielstone.materialaboutlibrary.MaterialAboutFragment;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.mikepenz.aboutlibraries.LibsBuilder;
import okhttp3.Headers;
import okhttp3.Request;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.ServerProperties;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.Util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AboutActivity extends AbstractBaseActivity implements
        FragmentManager.OnBackStackChangedListener  {
    @Override
    public void onCreate(Bundle savedInstanceState) {
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
            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager.getBackStackEntryCount() > 0) {
                fragmentManager.popBackStack();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        private static final String TAG = AboutMainFragment.class.getSimpleName();
        private static final String URL_TO_GITHUB = "https://github.com/openhab/openhab-android";
        private ServerProperties mServerProperties;
        private Connection mConnection;

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            mServerProperties = getArguments().getParcelable("serverProperties");
            try {
                mConnection = ConnectionFactory.Companion.getUsableConnection();
            } catch (ConnectionException ignored) {
                // ignored
            }
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
                    .setOnClickAction(clickRedirect(URL_TO_GITHUB + "/releases"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_source_code)
                    .icon(R.drawable.ic_github_grey_24dp)
                    .setOnClickAction(clickRedirect(URL_TO_GITHUB))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_issues)
                    .icon(R.drawable.ic_bug_outline_grey_24dp)
                    .setOnClickAction(clickRedirect(URL_TO_GITHUB + "/issues"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_license_title)
                    .subText(R.string.about_license)
                    .icon(R.drawable.ic_account_balance_grey_24dp)
                    .setOnClickAction(clickRedirect(URL_TO_GITHUB + "/blob/master/LICENSE"))
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.title_activity_libraries)
                    .icon(R.drawable.ic_code_braces_grey_24dp)
                    .setOnClickAction(() -> {
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
                    })
                    .build());
            appCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_privacy_policy)
                    .icon(R.drawable.ic_security_grey_24dp)
                    .setOnClickAction(
                            clickRedirect("https://www.openhabfoundation.org/privacy.html"))
                    .build());

            MaterialAboutCard.Builder ohServerCard = new MaterialAboutCard.Builder();
            ohServerCard.title(R.string.about_server);
            if (mConnection == null || mServerProperties == null) {
                ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                        .text(R.string.error_about_no_conn)
                        .icon(R.drawable.ic_info_outline_grey_24dp)
                        .build());
            } else {
                AsyncHttpClient httpClient = mConnection.getAsyncHttpClient();

                MaterialAboutActionItem apiVersionItem = new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_apiversion_label)
                        .subText(R.string.list_loading_message)
                        .icon(R.drawable.ic_info_outline_grey_24dp)
                        .build();
                ohServerCard.addItem(apiVersionItem);
                String versionUrl = useJsonApi() ? "rest" : "static/version";
                httpClient.get(versionUrl, new AsyncHttpClient.StringResponseHandler() {
                    @Override
                    public void onFailure(Request request, int statusCode, Throwable error) {
                        Log.e(TAG, "Could not rest API version " + error);
                        apiVersionItem.setSubText(getString(R.string.error_about_no_conn));
                        refreshMaterialAboutList();
                    }

                    @Override
                    public void onSuccess(String body, Headers headers) {
                        String version = "";
                        if (!useJsonApi()) {
                            version = body;
                        } else {
                            try {
                                JSONObject pageJson = new JSONObject(body);
                                version = pageJson.getString("version");
                            } catch (JSONException e) {
                                Log.e(TAG, "Problem fetching version string", e);
                            }
                        }

                        if (TextUtils.isEmpty(version)) {
                            version = getString(R.string.unknown);
                        }

                        Log.d(TAG, "Got api version " + version);
                        apiVersionItem.setSubText(version);
                        refreshMaterialAboutList();
                    }
                });

                MaterialAboutActionItem uuidItem = new MaterialAboutActionItem.Builder()
                        .text(R.string.info_openhab_uuid_label)
                        .subText(R.string.list_loading_message)
                        .icon(R.drawable.ic_info_outline_grey_24dp)
                        .build();
                ohServerCard.addItem(uuidItem);
                String uuidUrl = useJsonApi() ? "rest/uuid" : "static/uuid";
                httpClient.get(uuidUrl, new AsyncHttpClient.StringResponseHandler() {
                    @Override
                    public void onFailure(Request request, int statusCode, Throwable error) {
                        Log.e(TAG, "Could not fetch uuid " + error);
                        uuidItem.setSubText(getString(R.string.error_about_no_conn));
                        refreshMaterialAboutList();
                    }

                    @Override
                    public void onSuccess(String body, Headers headers) {
                        Log.d(TAG, "Got uuid " + Util.INSTANCE.obfuscateString(body));
                        uuidItem.setSubText(TextUtils.isEmpty(body)
                                ? getString(R.string.unknown)
                                : body);
                        refreshMaterialAboutList();
                    }
                });

                if (!useJsonApi()) {
                    MaterialAboutActionItem secretItem = new MaterialAboutActionItem.Builder()
                            .text(R.string.info_openhab_secret_label)
                            .subText(R.string.list_loading_message)
                            .icon(R.drawable.ic_info_outline_grey_24dp)
                            .build();
                    ohServerCard.addItem(secretItem);
                    httpClient.get("static/secret", new AsyncHttpClient.StringResponseHandler() {
                        @Override
                        public void onFailure(Request request, int statusCode, Throwable error) {
                            Log.e(TAG, "Could not fetch server secret " + error);
                            secretItem.setSubText(getString(R.string.error_about_no_conn));
                            refreshMaterialAboutList();
                        }

                        @Override
                        public void onSuccess(String body, Headers headers) {
                            Log.d(TAG, "Got secret " + Util.INSTANCE.obfuscateString(body));
                            secretItem.setSubText(TextUtils.isEmpty(body)
                                    ? getString(R.string.unknown)
                                    : body);
                            refreshMaterialAboutList();
                        }
                    });
                }
            }

            ohServerCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.info_openhab_push_notification_label)
                    .subText(CloudMessagingHelper.INSTANCE.getPushNotificationStatus(context))
                    .icon(CloudMessagingHelper.INSTANCE.getPushNotificationIconResId())
                    .build());

            MaterialAboutCard.Builder ohCommunityCard = new MaterialAboutCard.Builder();
            ohCommunityCard.title(R.string.about_community);
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_docs)
                    .icon(R.drawable.ic_file_document_box_multiple_outline_grey_24dp)
                    .setOnClickAction(clickRedirect("https://www.openhab.org/docs/"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_community_forum)
                    .icon(R.drawable.ic_forum_outline_grey_24dp)
                    .setOnClickAction(clickRedirect("https://community.openhab.org/"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_translation)
                    .icon(R.drawable.ic_translate_grey_24dp)
                    .setOnClickAction(clickRedirect("https://crowdin.com/profile/openhab-bot"))
                    .build());
            ohCommunityCard.addItem(new MaterialAboutActionItem.Builder()
                    .text(R.string.about_foundation)
                    .icon(R.drawable.ic_people_outline_grey_24dp)
                    .setOnClickAction(clickRedirect("https://www.openhabfoundation.org/"))
                    .build());

            return new MaterialAboutList.Builder()
                    .addCard(appCard.build())
                    .addCard(ohServerCard.build())
                    .addCard(ohCommunityCard.build())
                    .build();
        }

        @Override
        protected int getTheme() {
            return Util.INSTANCE.getActivityThemeId(getActivity());
        }

        private MaterialAboutItemOnClickAction clickRedirect(final String url) {
            return () -> {
                Util.openInBrowser(getContext(), url);
            };
        }

        private boolean useJsonApi() {
            return mServerProperties != null && mServerProperties.hasJsonApi();
        }
    }
}
