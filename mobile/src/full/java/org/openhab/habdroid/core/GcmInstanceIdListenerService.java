/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import com.google.android.gms.iid.InstanceIDListenerService;

public class GcmInstanceIdListenerService extends InstanceIDListenerService {
    @Override
    public void onTokenRefresh() {
        GcmRegistrationService.scheduleRegistration(this);
    }
}
