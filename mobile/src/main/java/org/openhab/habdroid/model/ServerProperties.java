package org.openhab.habdroid.model;

import android.os.Parcelable;
import android.util.Log;

import com.google.auto.value.AutoValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;

@AutoValue
public abstract class ServerProperties implements Parcelable {
    private static final String TAG = ServerProperties.class.getSimpleName();

    public static final int SERVER_FLAG_JSON_REST_API         = 1 << 0;
    public static final int SERVER_FLAG_SSE_SUPPORT           = 1 << 1;
    public static final int SERVER_FLAG_ICON_FORMAT_SUPPORT   = 1 << 2;
    public static final int SERVER_FLAG_CHART_SCALING_SUPPORT = 1 << 3;

    public static class UpdateHandle {
        public void cancel() {
            if (call != null) {
                call.cancel();
                call = null;
            }
        }

        private Call call;
        private Builder builder;
    }

    public interface UpdateSuccessCallback {
        void handleServerPropertyUpdate(ServerProperties props);
    }
    public interface UpdateFailureCallback {
        void handleUpdateFailure(Request request, int statusCode, Throwable error);
    }

    public abstract int flags();
    public abstract List<OpenHABSitemap> sitemaps();

    public boolean hasJsonApi() {
        return (flags() & SERVER_FLAG_JSON_REST_API) != 0;
    }

    public boolean hasSseSupport() {
        return (flags() & SERVER_FLAG_SSE_SUPPORT) != 0;
    }

    abstract Builder toBuilder();

    @AutoValue.Builder
    static abstract class Builder {
        abstract Builder flags(int flags);
        abstract Builder sitemaps(List<OpenHABSitemap> sitemaps);

        abstract ServerProperties build();
        abstract int flags();
    }

    public static UpdateHandle updateSitemaps(ServerProperties props, Connection connection,
            UpdateSuccessCallback successCb, UpdateFailureCallback failureCb) {
        UpdateHandle handle = new UpdateHandle();
        handle.builder = props.toBuilder();
        fetchSitemaps(connection.getAsyncHttpClient(), handle, successCb, failureCb);
        return handle;
    }

    public static UpdateHandle fetch(Connection connection,
            UpdateSuccessCallback successCb, UpdateFailureCallback failureCb) {
        final UpdateHandle handle = new UpdateHandle();
        handle.builder = new AutoValue_ServerProperties.Builder();
        fetchFlags(connection.getAsyncHttpClient(), handle, successCb, failureCb);
        return handle;
    }

    private static void fetchFlags(AsyncHttpClient client, UpdateHandle handle,
            UpdateSuccessCallback successCb, UpdateFailureCallback failureCb) {
        handle.call = client.get("rest", new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                failureCb.handleUpdateFailure(request, statusCode, error);
            }

            @Override
            public void onSuccess(String response, Headers headers) {
                try {
                    JSONObject result = new JSONObject(response);
                    // If this succeeded, we're talking to OH2
                    int flags = SERVER_FLAG_JSON_REST_API
                            | SERVER_FLAG_ICON_FORMAT_SUPPORT
                            | SERVER_FLAG_CHART_SCALING_SUPPORT;
                    try {
                        String versionString = result.getString("version");
                        int versionNumber = Integer.parseInt(versionString);
                        // all versions that return a number here have full SSE support
                        flags |= SERVER_FLAG_SSE_SUPPORT;
                    } catch (NumberFormatException nfe) {
                        // ignored: older versions without SSE support didn't return a number
                    }
                    handle.builder.flags(flags);
                    fetchSitemaps(client, handle, successCb, failureCb);
                } catch (JSONException e) {
                    if (response.startsWith("<?xml")) {
                        // We're talking to an OH1 instance
                        handle.builder.flags(0);
                        fetchSitemaps(client, handle, successCb, failureCb);
                    } else {
                        failureCb.handleUpdateFailure(handle.call.request(), 200, e);
                    }
                }
            }
        });
    }

    private static void fetchSitemaps(AsyncHttpClient client, UpdateHandle handle,
            UpdateSuccessCallback successCb, UpdateFailureCallback failureCb) {
        handle.call = client.get("rest/sitemaps", new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                failureCb.handleUpdateFailure(request, statusCode, error);
            }

            @Override
            public void onSuccess(String response, Headers headers) {
                // OH1 returns XML, later versions return JSON
                List<OpenHABSitemap> result = (handle.builder.flags() & SERVER_FLAG_JSON_REST_API) != 0
                        ? loadSitemapsFromJson(response)
                        : loadSitemapsFromXml(response);
                Log.d(TAG, "Server returned sitemaps: " + result);
                handle.builder.sitemaps(result != null ? result : new ArrayList<>());
                successCb.handleServerPropertyUpdate(handle.builder.build());
            }
        });
    }

    private static List<OpenHABSitemap> loadSitemapsFromXml(String response) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document sitemapsXml = builder.parse(new InputSource(new StringReader(response)));
            return Util.parseSitemapList(sitemapsXml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, "Failed parsing sitemap XML", e);
            return null;
        }
    }

    private static List<OpenHABSitemap> loadSitemapsFromJson(String response) {
        try {
            JSONArray jsonArray = new JSONArray(response);
            return Util.parseSitemapList(jsonArray);
        } catch (JSONException e) {
            Log.e(TAG, "Failed parsing sitemap JSON", e);
            return null;
        }
    }
}
