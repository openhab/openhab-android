package org.openhab.habdroid

import android.view.View
import androidx.core.view.isInvisible
import androidx.test.espresso.IdlingResource

class ProgressbarIdlingResource(private val name: String, private val progressBar: View) : IdlingResource {
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String {
        return name
    }

    override fun isIdleNow(): Boolean {
        val idle = progressBar.isInvisible
        if (idle) {
            callback?.onTransitionToIdle()
        }

        return idle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
}
