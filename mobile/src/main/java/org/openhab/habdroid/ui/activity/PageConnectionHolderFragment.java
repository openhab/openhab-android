package org.openhab.habdroid.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;

public class PageConnectionHolderFragment extends Fragment {
    public interface ParentCallback {
        boolean serverReturnsJson();
        void onPageUpdated(String pageUrl, String pageTitle, List<OpenHABWidget> widgets);
    }

    private Map<String, ConnectionHandler> mConnections = new HashMap<>();
    private ParentCallback mCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCallback = (ParentCallback) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        for (ConnectionHandler handler : mConnections.values()) {
            handler.cancel();
        }
        mConnections.clear();
    }

    public void updateActiveConnections(List<String> urls) {
        List<String> toRemove = new ArrayList<>();
        for (String url : mConnections.keySet()) {
            if (!urls.contains(url)) {
                toRemove.add(url);
            }
        }
        for (String url : toRemove) {
            mConnections.remove(url).cancel();
        }
        for (String url : urls) {
            if (!mConnections.containsKey(url)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                MyAsyncHttpClient httpClient = new MyAsyncHttpClient(getActivity(),
                        prefs.getBoolean(Constants.PREFERENCE_SSLHOST, false),
                        prefs.getBoolean(Constants.PREFERENCE_SSLCERT, false));
                ConnectionHandler handler = new ConnectionHandler(url, httpClient, mCallback);
                mConnections.put(url, handler);
            }
        }
    }

    public void triggerUpdate(String pageUrl, boolean forceReload) {
        ConnectionHandler handler = mConnections.get(pageUrl);
        if (handler != null) {
            handler.triggerUpdate(forceReload);
        }
    }

    private static class ConnectionHandler implements MyHttpClient.ResponseHandler {
        private final String mUrl;
        private final MyAsyncHttpClient mHttpClient;
        private final ParentCallback mCallback;
        private Call mRequestHandle;
        private boolean mLongPolling;
        private String mAtmosphereTrackingId;
        private String mLastPageTitle;
        private List<OpenHABWidget> mLastWidgetList;

        public ConnectionHandler(String pageUrl, MyAsyncHttpClient httpClient, ParentCallback cb) {
            mUrl = pageUrl;
            mHttpClient = httpClient;
            mCallback = cb;
            load();
        }

        public void cancel() {
            if (mRequestHandle != null) {
                mRequestHandle.cancel();
                mRequestHandle = null;
            }
        }

        public void triggerUpdate(boolean forceReload) {
            if (forceReload) {
                mLongPolling = false;
                load();
            } else if (mLastWidgetList != null) {
                mCallback.onPageUpdated(mUrl, mLastPageTitle, mLastWidgetList);
            }
        }

        private void load() {
            cancel();

            Map<String, String> headers = new HashMap<String, String>();
            if (!mCallback.serverReturnsJson()) {
                headers.put("Accept", "application/xml");
            }

            if (mLongPolling) {
                mHttpClient.setTimeout(300000);
                headers.put("X-Atmosphere-Transport", "long-polling");
            } else {
                mAtmosphereTrackingId = null;
                mHttpClient.setTimeout(10000);
            }

            headers.put("X-Atmosphere-Framework", "1.0");
            headers.put("X-Atmosphere-tracking-id",
                    mAtmosphereTrackingId != null ? mAtmosphereTrackingId : "0");

            mRequestHandle = mHttpClient.get(mUrl, headers, this);
        }

        @Override
        public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
            if (call.isCanceled()) {
                return;
            }
            mAtmosphereTrackingId = null;
            mLongPolling = false;
            load();
        }

        @Override
        public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
            if (call.isCanceled()) {
                return;
            }
            String id = headers.get("X-Atmosphere-tracking-id");
            if (id != null) {
                mAtmosphereTrackingId = id;
            }
            processContent(new String(responseBody));
        }

        private void processContent(String responseString) {
            // We can receive empty response, probably when no items was changed
            // so we needn't process it
            if (responseString == null || responseString.isEmpty()) {
                mLongPolling = true;
                load();
                return;
            }

            OpenHABWidgetDataSource dataSource = new OpenHABWidgetDataSource("PNG"); // XXX
            final boolean hasUpdate;
            if (mCallback.serverReturnsJson()) {
                hasUpdate = parseResponseJson(dataSource, responseString);
            } else {
                hasUpdate = parseResponseXml(dataSource, responseString);
            }

            if (hasUpdate) {
                List<OpenHABWidget> widgetList = new ArrayList<>();
                for (OpenHABWidget w : dataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel())) {
                        continue;
                    }
                    widgetList.add(w);
                }

                mLastPageTitle = dataSource.getTitle();
                mLastWidgetList = widgetList;
                mCallback.onPageUpdated(mUrl, mLastPageTitle, mLastWidgetList);
            }

            load();
        }

        private boolean parseResponseXml(OpenHABWidgetDataSource dataSource, String response) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = dbf.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(response)));
                if (document == null) {
                    mLongPolling = false;
                    return false;
                }
                Node rootNode = document.getFirstChild();
                dataSource.setSourceNode(rootNode);
                mLongPolling = true;
                return true;
            } catch (ParserConfigurationException | SAXException | IOException e) {
                mLongPolling = false;
                return false;
            }
        }

        private boolean parseResponseJson(OpenHABWidgetDataSource dataSource, String response) {
            try {
                JSONObject pageJson = new JSONObject(response);
                // In case of a server timeout in the long polling request, nothing is done
                // and the request is restarted
                if (mLongPolling && pageJson.optBoolean("timeout", false)) {
                    return false;
                }
                dataSource.setSourceJson(pageJson);
                mLongPolling = true;
                return true;
            } catch (JSONException e) {
                mLongPolling = false;
                return false;
            }
        }
    }
}
