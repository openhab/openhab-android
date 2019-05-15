package org.openhab.habdroid

import androidx.fragment.app.FragmentManager
import androidx.test.espresso.IdlingResource

import org.openhab.habdroid.ui.WidgetListFragment
import org.openhab.habdroid.ui.activity.ContentController

class FragmentStatusIdlingResource(private val name: String, private val fm: FragmentManager) : IdlingResource {
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String {
        return name
    }

    override fun isIdleNow(): Boolean {
        val idle = !hasBusyFragments()
        if (idle) {
            callback?.onTransitionToIdle()
        }

        return idle
    }

    private fun hasBusyFragments(): Boolean {
        if (fm.isDestroyed) {
            return false
        }
        fm.executePendingTransactions()
        for (f in fm.fragments) {
            if (f is ContentController.ProgressFragment) {
                return true
            }
            if (f is WidgetListFragment) {
                if (f.recyclerView.hasPendingAdapterUpdates()) {
                    return true
                }
            }
        }
        return false
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
}
