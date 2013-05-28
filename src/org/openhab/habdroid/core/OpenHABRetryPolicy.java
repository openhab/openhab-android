/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.core;

import android.util.Log;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;

public class OpenHABRetryPolicy implements RetryPolicy {

    private static final String TAG = "OpenHABRetryPolicy";
    private int mCurrentTimeoutMs;
    private int mMaxNumRetries;
    private int mCurrentRetryCount;
    private float mBackoffMultiplier;

    /** The default socket timeout in milliseconds - 10 minutes */
    public static final int DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;
    /** The default number of retries - retry forever */
    public static final int DEFAULT_MAX_RETRIES = 0;
    /** The default backoff multiplier - factor of 1 */
    public static final float DEFAULT_BACKOFF_MULT = 1f;

    public OpenHABRetryPolicy() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MULT);
    }

    public OpenHABRetryPolicy(int initialTimeoutMs, int maxNumRetries, float backoffMultiplier) {
        mCurrentTimeoutMs = initialTimeoutMs;
        mMaxNumRetries = maxNumRetries;
        mBackoffMultiplier = backoffMultiplier;
    }

    public int getCurrentTimeout() {
        return mCurrentTimeoutMs;
    }

    public int getCurrentRetryCount() {
        return mCurrentRetryCount;
    }

    public void retry(VolleyError volleyError) throws VolleyError {
        mCurrentRetryCount++;
        mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackoffMultiplier);
        Log.d(TAG, String.format("Retry %d", mCurrentRetryCount));
        if (!hasAttemptRemaining())
            throw volleyError;
    }

    protected boolean hasAttemptRemaining() {
        if (mMaxNumRetries > 0)
            return mCurrentRetryCount <= mMaxNumRetries;
        return true;
    }
}
