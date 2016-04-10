/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import javax.jmdns.ServiceInfo;

public interface AsyncServiceResolverListener {
	public void onServiceResolved(ServiceInfo serviceInfo);
	public void onServiceResolveFailed();
}
