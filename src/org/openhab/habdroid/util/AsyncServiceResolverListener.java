package org.openhab.habdroid.util;

import javax.jmdns.ServiceInfo;

public interface AsyncServiceResolverListener {
	public void onServiceResolved(ServiceInfo serviceInfo);
	public void onServiceResolveFailed();
}
