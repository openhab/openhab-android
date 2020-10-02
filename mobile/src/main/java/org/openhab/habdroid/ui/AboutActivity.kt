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
import android.view.MenuItem
import androidx.annotation.DrawableRes
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.util.ScreenLockMode
import org.openhab.habdroid.util.Util
import org.openhab.habdroid.util.openInAppStore
import org.openhab.habdroid.util.openInBrowser

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

    class AboutMainFragment : MaterialAboutFragment() {
        override fun getMaterialAboutList(context: Context): MaterialAboutList {
            val year = SimpleDateFormat("yyyy", Locale.US).format(Calendar.getInstance().time)

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
                .subText(BuildConfig.VERSION_NAME)
                .icon(R.drawable.ic_update_grey_24dp)
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_changelog)
                .icon(R.drawable.ic_track_changes_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "$URL_TO_GITHUB/releases"))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_source_code)
                .icon(R.drawable.ic_github_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, URL_TO_GITHUB))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_issues)
                .icon(R.drawable.ic_bug_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "$URL_TO_GITHUB/issues"))
                .build())
            appCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_license_title)
                .subText(R.string.about_license)
                .icon(R.drawable.ic_account_balance_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "$URL_TO_GITHUB/blob/master/LICENSE"))
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
                .setOnClickAction(makeClickRedirect(context, "https://www.openhabfoundation.org/privacy.html"))
                .build())

            val ohCommunityCard = MaterialAboutCard.Builder()
                .title(R.string.about_community)
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_docs)
                .icon(R.drawable.ic_file_document_box_multiple_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "https://www.openhab.org/docs/apps/android.html"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_community_forum)
                .icon(R.drawable.ic_forum_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "https://community.openhab.org/"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_translation)
                .icon(R.drawable.ic_translate_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "https://crowdin.com/profile/openhab-bot"))
                .build())
            ohCommunityCard.addItem(MaterialAboutActionItem.Builder()
                .text(R.string.about_foundation)
                .icon(R.drawable.ic_people_outline_grey_24dp)
                .setOnClickAction(makeClickRedirect(context, "https://www.openhabfoundation.org/"))
                .build())

            return MaterialAboutList.Builder()
                .addCard(appCard.build())
                .addCard(ohCommunityCard.build())
                .build()
        }

        override fun getTheme(): Int {
            return Util.getActivityThemeId(requireContext())
        }

        companion object {
            private const val URL_TO_GITHUB = "https://github.com/openhab/openhab-android"

            fun makeClickRedirect(context: Context, url: String) = MaterialAboutItemOnClickAction {
                url.toUri().openInBrowser(context)
            }
        }
    }
}

data class PushNotificationStatus(
    val message: String,
    @DrawableRes val icon: Int,
    val notifyUser: Boolean
)
