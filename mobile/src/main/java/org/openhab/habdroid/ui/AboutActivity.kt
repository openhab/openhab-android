/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import com.mikepenz.aboutlibraries.LibsBuilder
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.connection.CloudConnection
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.openInAppStore
import org.openhab.habdroid.util.openInBrowser
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AboutActivity : AbstractBaseActivity(), FragmentManager.OnBackStackChangedListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)
        supportFragmentManager.addOnBackStackChangedListener(this)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val f = AboutMainFragment()
            f.arguments = intent.extras
            supportFragmentManager.commit {
                add(R.id.activity_content, f)
            }
        }

        updateTitle()
        setResult(RESULT_OK)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val fragmentManager = supportFragmentManager
            if (fragmentManager.backStackEntryCount > 0) {
                fragmentManager.popBackStack()
            } else {
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackStackChanged() {
        updateTitle()
    }

    override fun doesLockModeRequirePrompt(mode: ScreenLockMode): Boolean {
        return mode == ScreenLockMode.Enabled
    }

    private fun updateTitle() {
        val fm = supportFragmentManager
        val titleResId = when (fm.findFragmentById(R.id.activity_content)) {
            is LibsSupportFragment -> R.string.title_activity_libraries
            else -> R.string.about_title
        }
        setTitle(titleResId)
    }

    class AboutMainFragment : MaterialAboutFragment(), ConnectionFactory.UpdateListener {
        private lateinit var pushStatusCard: MaterialAboutActionItem

        override fun onStart() {
            super.onStart()
            ConnectionFactory.addListener(this)
        }

        override fun onStop() {
            super.onStop()
            ConnectionFactory.removeListener(this)
        }

        override fun getMaterialAboutList(context: Context): MaterialAboutList {
            val props: ServerProperties? = arguments?.getParcelable("serverProperties")
            val connection = ConnectionFactory.usableConnectionOrNull
            val year = SimpleDateFormat("yyyy", Locale.US).format(Calendar.getInstance().time)

            val makeClickRedirect: (url: String) -> MaterialAboutItemOnClickAction = {
                MaterialAboutItemOnClickAction { it.toUri().openInBrowser(context) }
            }

            val appCard = MaterialAboutCard.Builder()
            appCard.addItem(MaterialAboutTitleItem.Builder()
                .text(R.string.app_name)
                .desc(context.getString(R.string.about_copyright, year))
                .icon(R.mipmap.icon)
                .build())
            if (Util.isFlavorStable) {
                appCard.addItem(MaterialAboutActionItem.Builder()
                    .text(R.string.about_rate_this_app)
                    .icon(R.drawable.ic_star_border_grey_24dp)
                    .setOnClickAction { context.openInAppStore(context.packageName) }
                    .build()
                )
            }
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.version)
                .subText(context.getString(R.string.about_version_string,
                    BuildConfig.VERSION_NAME,
                    DateFormat.getDateTimeInstance().format(BuildConfig.buildTime)))
                .icon(R.drawable.ic_update_grey_24dp)
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_changelog)
                .icon(R.drawable.ic_track_changes_grey_24dp)
                .setOnClickAction(makeClickRedirect("$URL_TO_GITHUB/releases"))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_source_code)
                .icon(R.drawable.ic_github_grey_24dp)
                .setOnClickAction(makeClickRedirect(URL_TO_GITHUB))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_issues)
                .icon(R.drawable.ic_bug_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect("$URL_TO_GITHUB/issues"))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_license_title)
                .subText(R.string.about_license)
                .icon(R.drawable.ic_account_balance_grey_24dp)
                .setOnClickAction(makeClickRedirect("$URL_TO_GITHUB/blob/master/LICENSE"))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.title_activity_libraries)
                .icon(R.drawable.ic_code_braces_grey_24dp)
                .setOnClickAction {
                    val f = LibsBuilder()
                        .withFields(R.string::class.java.fields)
                        .withLicenseShown(true)
                        .withAutoDetect(true)
                        .withAboutIconShown(false)
                        .withAboutVersionShown(false)
                        .withAboutVersionShownCode(false)
                        .supportFragment()
                    parentFragmentManager.commit {
                        setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right)
                        replace(R.id.activity_content, f)
                        addToBackStack(null)
                    }
                }
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_privacy_policy)
                .icon(R.drawable.ic_security_grey_24dp)
                .setOnClickAction(makeClickRedirect("https://www.openhabfoundation.org/privacy.html"))
                .build())

            val ohServerCard = MaterialAboutCard.Builder()
                .title(R.string.about_server)

            if (connection == null || props == null) {
                ohServerCard.addItem(MaterialAboutActionItem.Builder()
                    .text(R.string.error_about_no_conn)
                    .icon(R.drawable.ic_info_outline_grey_24dp)
                    .build())
            } else {
                val scope = activity as AboutActivity
                val httpClient = connection.httpClient
                val apiVersionItem = MaterialAboutActionItem.Builder()
                    .text(R.string.info_openhab_apiversion_label)
                    .subText(R.string.list_loading_message)
                    .icon(R.drawable.ic_info_outline_grey_24dp)
                    .build()
                ohServerCard.addItem(apiVersionItem)
                val versionUrl = if (props.hasJsonApi()) "rest" else "static/version"
                scope.launch {
                    try {
                        val response = httpClient.get(versionUrl).asText().response
                        var version = ""
                        if (!props.hasJsonApi()) {
                            version = response
                        } else {
                            try {
                                val pageJson = JSONObject(response)
                                version = pageJson.getString("version")
                            } catch (e: JSONException) {
                                Log.e(TAG, "Problem fetching version string", e)
                            }
                        }

                        if (version.isEmpty()) {
                            version = getString(R.string.unknown)
                        }

                        Log.d(TAG, "Got api version $version")
                        apiVersionItem.subText = version
                    } catch (e: HttpClient.HttpException) {
                        Log.e(TAG, "Could not rest API version $e")
                        apiVersionItem.subText = getString(R.string.error_about_no_conn)
                    }
                    refreshMaterialAboutList()
                }
            }

            pushStatusCard = MaterialAboutActionItem.Builder()
                .text(R.string.info_openhab_push_notification_label)
                .subText(R.string.list_loading_message)
                .icon(R.drawable.ic_bell_outline_grey_24dp)
                .build()
            ohServerCard.addItem(pushStatusCard)
            updatePushStatusCard()

            val ohCommunityCard = MaterialAboutCard.Builder()
                .title(R.string.about_community)
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_docs)
                .icon(R.drawable.ic_file_document_box_multiple_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect("https://www.openhab.org/docs/"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_community_forum)
                .icon(R.drawable.ic_forum_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect("https://community.openhab.org/"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_translation)
                .icon(R.drawable.ic_translate_grey_24dp)
                .setOnClickAction(makeClickRedirect("https://crowdin.com/profile/openhab-bot"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_foundation)
                .icon(R.drawable.ic_people_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect("https://www.openhabfoundation.org/"))
                .build())

            return MaterialAboutList.Builder()
                .addCard(appCard.build())
                .addCard(ohServerCard.build())
                .addCard(ohCommunityCard.build())
                .build()
        }

        override fun getTheme(): Int {
            return Util.getActivityThemeId(requireContext())
        }

        private fun updatePushStatusCard() {
            val scope = activity as AboutActivity
            scope.launch {
                Log.d(TAG, "Updating push notification status card")
                val data = CloudMessagingHelper.getPushNotificationStatus(requireContext())
                pushStatusCard.subText = data.first
                pushStatusCard.icon = ContextCompat.getDrawable(requireContext(), data.second)
                refreshMaterialAboutList()
            }
        }

        override fun onAvailableConnectionChanged() {
            updatePushStatusCard()
        }

        override fun onCloudConnectionChanged(connection: CloudConnection?) {
            updatePushStatusCard()
        }

        companion object {
            private val TAG = AboutMainFragment::class.java.simpleName
            private const val URL_TO_GITHUB = "https://github.com/openhab/openhab-android"
        }
    }
}
