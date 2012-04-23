package org.openhab.habdroid.util;

import java.io.IOException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

public class AsyncServiceResolver extends Thread implements ServiceListener {
	private final static String TAG = "AsyncServiceResolver";
	private Context context;
	// Multicast lock for mDNS
	private MulticastLock multicastLock;
	// mDNS service
	private JmDNS jmdns;
	private String serviceType;
	private ServiceInfo resolvedServiceInfo;
	private Thread sleepingThread;
	private boolean isResolved = false;
	
	public AsyncServiceResolver(Context context, String serviceType) {
		super();
		this.context = context;
		this.serviceType = serviceType;
	}
	
	public void run() {
		WifiManager wifi =
		           (android.net.wifi.WifiManager)
		              context.getSystemService(android.content.Context.WIFI_SERVICE);
		multicastLock = wifi.createMulticastLock("HABDroidMulticastLock");
		multicastLock.setReferenceCounted(true);
		multicastLock.acquire();
		sleepingThread = Thread.currentThread();
		Log.i(TAG, "Discovering service " + serviceType);
		try {
			jmdns = JmDNS.create();
			jmdns.addServiceListener(serviceType, this);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		try {
			sleepingThread.sleep(5000);
			if (!isResolved) {
				((Activity)context).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						((AsyncServiceResolverListener)context).onServiceResolveFailed();
					}
				});
				shutdown();
			}
		} catch (InterruptedException e) {
		}
	}

	@Override
	public void serviceAdded(ServiceEvent event) {
		jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
	}

	@Override
	public void serviceRemoved(ServiceEvent event) {
	}

	@Override
	public void serviceResolved(ServiceEvent event) {
		resolvedServiceInfo = event.getInfo();
		isResolved = true;
		((Activity)context).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((AsyncServiceResolverListener)context).onServiceResolved(resolvedServiceInfo);
			}
		});
		shutdown();
		sleepingThread.interrupt();
	}

	private void shutdown() {
		if (multicastLock != null)
			multicastLock.release();
		if (jmdns != null) {
			jmdns.removeServiceListener(serviceType, this);
			try {
				jmdns.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}
}
