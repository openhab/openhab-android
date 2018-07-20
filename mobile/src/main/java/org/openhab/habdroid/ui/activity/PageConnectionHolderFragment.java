package org.openhab.habdroid.ui.activity;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;

/**
 * Fragment that manages connections for active instances of
 * {@link org.openhab.habdroid.ui.OpenHABWidgetListFragment}
 *
 * It retains the connections over activity recreations, and takes care of stopping
 * and restarting connections if needed.
 */
public class PageConnectionHolderFragment extends Fragment {
    private static final String TAG = PageConnectionHolderFragment.class.getSimpleName();

    public interface ParentCallback {
        /**
         * Ask parent whether server returns JSON or XML
         * @return true if server returns JSON, false if it returns XML
         */
        boolean serverReturnsJson();

        /**
         * Ask parent for the icon format to use
         * @return Icon format ('PNG' or 'SVG')
         */
        String getIconFormat();

        /**
         * Let parent know about an update to the widget list for a given URL.
         * @param pageUrl URL of the updated page
         * @param pageTitle Updated page title
         * @param widgets Updated list of widgets for the given page
         */
        void onPageUpdated(String pageUrl, String pageTitle, List<OpenHABWidget> widgets);

        /**
         * Let parent know about an update to the contents of a single widget.
         * @param widget Updated widget
         */
        void onWidgetUpdated(OpenHABWidget widget);
    }

    private Map<String, ConnectionHandler> mConnections = new HashMap<>();
    private ParentCallback mCallback;
    private boolean mStarted;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart(), started " + mStarted);
        if (!mStarted) {
            for (ConnectionHandler handler : mConnections.values()) {
                handler.load();
            }
            mStarted = true;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
        // If the activity is only changing configuration (e.g. orientation or locale)
        // we know it'll be immediately recreated, thus there's no point in shutting down
        // the connections in that case
        if (!getActivity().isChangingConfigurations()) {
            for (ConnectionHandler handler : mConnections.values()) {
                handler.cancel();
            }
            mStarted = false;
        }
    }

    /**
     * Assign parent callback
     *
     * To be called by the parent as early as possible,
     * as it's expected to be non-null at all times
     *
     * @param callback Callback for parent
     */
    public void setCallback(ParentCallback callback) {
        mCallback = callback;
        for (ConnectionHandler handler : mConnections.values()) {
            handler.mCallback = mCallback;
        }
    }

    /**
     * Update list of page URLs to track
     *
     * @param urls New list of URLs to track
     * @param connection Connection to use, or null if none is available
     */
    public void updateActiveConnections(List<String> urls, Connection connection) {
        Log.d(TAG, "updateActiveConnections: URL list " + urls + ", connection " + connection);
        if (connection == null) {
            for (ConnectionHandler handler : mConnections.values()) {
                handler.cancel();
            }
            mConnections.clear();
            return;
        }

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
            ConnectionHandler handler = mConnections.get(url);
            if (handler == null) {
                Log.d(TAG, "Creating new handler for URL " + url);
                handler = new ConnectionHandler(url, connection, mCallback);
                mConnections.put(url, handler);
                if (mStarted) {
                    handler.load();
                }
            } else if (handler.updateFromConnection(connection) && mStarted) {
                handler.load();
            }
        }
    }

    /**
     * Ask for new data to be delivered for a given page
     *
     * @param pageUrl URL of page to trigger update for
     * @param forceReload true if existing data should be discarded and new data be loaded,
     *                    false if only existing data should be delivered, if it exists
     */
    public void triggerUpdate(String pageUrl, boolean forceReload) {
        ConnectionHandler handler = mConnections.get(pageUrl);
        if (handler != null) {
            handler.triggerUpdate(forceReload);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s [%d connections, started=%s]",
                super.toString(), mConnections.size(), mStarted);
    }

    private static class ConnectionHandler extends AsyncHttpClient.StringResponseHandler {
        private final String mUrl;
        private AsyncHttpClient mHttpClient;
        private ParentCallback mCallback;
        private Call mRequestHandle;
        private boolean mLongPolling;
        private String mAtmosphereTrackingId;
        private String mLastPageTitle;
        private List<OpenHABWidget> mLastWidgetList;

        public ConnectionHandler(String pageUrl, Connection connection, ParentCallback cb) {
            mUrl = pageUrl;
            mHttpClient = connection.getAsyncHttpClient();
            mCallback = cb;
        }

        public boolean updateFromConnection(Connection c) {
            AsyncHttpClient oldClient = mHttpClient;
            mHttpClient = c.getAsyncHttpClient();
            return oldClient != mHttpClient;
        }

        public void cancel() {
            Log.d(TAG, "Canceling connection for URL " + mUrl);
            if (mRequestHandle != null) {
                mRequestHandle.cancel();
                mRequestHandle = null;
            }
            mLongPolling = false;
        }

        public void triggerUpdate(boolean forceReload) {
            Log.d(TAG, "Trigger update for URL " + mUrl + ", force " + forceReload);
            if (forceReload) {
                mLongPolling = false;
                load();
            } else if (mLastWidgetList != null) {
                mCallback.onPageUpdated(mUrl, mLastPageTitle, mLastWidgetList);
            }
        }

        private void load() {
            Log.d(TAG, "Loading data for " + mUrl);
            Map<String, String> headers = new HashMap<String, String>();
            if (!mCallback.serverReturnsJson()) {
                headers.put("Accept", "application/xml");
            }

            if (mLongPolling) {
                headers.put("X-Atmosphere-Transport", "long-polling");
            } else {
                mAtmosphereTrackingId = null;
            }

            headers.put("X-Atmosphere-Framework", "1.0");
            headers.put("X-Atmosphere-tracking-id",
                    mAtmosphereTrackingId != null ? mAtmosphereTrackingId : "0");

            if (mRequestHandle != null) {
                mRequestHandle.cancel();
            }
            final long timeoutMillis = mLongPolling ? 300000 : 10000;
            mRequestHandle = mHttpClient.get(mUrl, headers, timeoutMillis, this);
        }

        @Override
        public void onFailure(Request request, int statusCode, Throwable error) {
            Log.d(TAG, "Data load for " + mUrl + " failed", error);
            mAtmosphereTrackingId = null;
            mLongPolling = false;
            load();
        }

        @Override
        public void onSuccess(String response, Headers headers) {
            String id = headers.get("X-Atmosphere-tracking-id");
            if (id != null) {
                mAtmosphereTrackingId = id;
            }

            // We can receive empty response, probably when no items was changed
            // so we needn't process it
            if (response == null || response.isEmpty()) {
                Log.d(TAG, "Got empty data response for " + mUrl);
                mLongPolling = true;
                load();
                return;
            }

            OpenHABWidgetDataSource dataSource =
                    new OpenHABWidgetDataSource(mCallback.getIconFormat());
            final boolean hasUpdate;
            if (mCallback.serverReturnsJson()) {
                hasUpdate = parseResponseJson(dataSource, response);
            } else {
                hasUpdate = parseResponseXml(dataSource, response);
            }

            if (hasUpdate) {
                List<OpenHABWidget> widgetList = new ArrayList<>();
                for (OpenHABWidget w : dataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.type() == OpenHABWidget.Type.Frame && TextUtils.isEmpty(w.label())) {
                        continue;
                    }
                    widgetList.add(w);
                }

                Log.d(TAG, "Updated page data for URL " + mUrl + ": widget list " + widgetList);
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
                    Log.d(TAG, "Got empty XML document for " + mUrl);
                    mLongPolling = false;
                    return false;
                }
                Node rootNode = document.getFirstChild();
                dataSource.setSourceNode(rootNode);
                mLongPolling = true;
                return true;
            } catch (ParserConfigurationException | SAXException | IOException e) {
                Log.d(TAG, "Parsing data for " + mUrl + " failed", e);
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
                    Log.d(TAG, "Long polling timeout for " + mUrl);
                    return false;
                }
                dataSource.setSourceJson(pageJson);
                mLongPolling = true;
                return true;
            } catch (JSONException e) {
                Log.d(TAG, "Parsing data for " + mUrl + " failed", e);
                mLongPolling = false;
                return false;
            }
        }
    }
}
