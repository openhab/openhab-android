package org.openhab.habdroid.core.sitemap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;

public class SitemapList implements Collection<OpenHABSitemap> {
    private static final String TAG = SitemapList.class.getSimpleName();

    private final List<OpenHABSitemap> sitemaps = new ArrayList<>();
    private MyAsyncHttpClient httpClient;
    private Dialog selectSitemapDialog;

    public SitemapList(@NonNull MyAsyncHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public int size() {
        return sitemaps.size();
    }

    @Override
    public boolean isEmpty() {
        return sitemaps.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return sitemaps.contains(o);
    }

    @NonNull
    @Override
    public Iterator<OpenHABSitemap> iterator() {
        return sitemaps.iterator();
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return sitemaps.toArray();
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull T[] ts) {
        return sitemaps.toArray(ts);
    }

    @Override
    public boolean add(OpenHABSitemap openHABSitemap) {
        return sitemaps.add(openHABSitemap);
    }

    @Override
    public boolean remove(Object o) {
        return sitemaps.remove(o);
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> collection) {
        return sitemaps.containsAll(collection);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends OpenHABSitemap> collection) {
        return sitemaps.addAll(collection);
    }

    public boolean addParcelable(Collection<? extends Parcelable> collection) {
        if (collection == null)
            return true;

        try {
            return addAll((Collection<OpenHABSitemap>) collection);
        } catch (ClassCastException ex) {
            return false;
        }
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> collection) {
        return sitemaps.removeAll(collection);
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> collection) {
        return sitemaps.retainAll(collection);
    }

    @Override
    public void clear() {
        sitemaps.clear();
    }

    public void loadSitemapList(String baseUrl, @NonNull final SitemapLoadCallback cb) {
        Log.d(TAG, "Loading sitemap list from " + baseUrl + "rest/sitemaps");
        final SitemapList self = this;

        httpClient.get(baseUrl + "rest/sitemaps", new MyHttpClient.ResponseHandler() {

            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                cb.onFailure(statusCode, error);
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                Log.d(TAG, new String(responseBody));
                sitemaps.clear();
                // If openHAB's version is 1, get sitemap list from XML
                if (headers.get("Content-Type").equals("application/xml")) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder builder = dbf.newDocumentBuilder();
                        Document sitemapsXml = builder.parse(new ByteArrayInputStream(responseBody));
                        sitemaps.addAll(Util.parseSitemapList(sitemapsXml));
                    } catch (ParserConfigurationException | SAXException | IOException e) {
                        Log.d(TAG, "Could not parse XML", e);
                        cb.onFailure(statusCode, e);
                        return;
                    }
                    cb.onSuccess(self);
                    return;
                }
                // Later versions work with JSON
                try {
                    String jsonString = new String(responseBody, "UTF-8");
                    JSONArray jsonArray = new JSONArray(jsonString);
                    sitemaps.addAll(Util.parseSitemapList(jsonArray));
                    Log.d(TAG, jsonArray.toString());
                } catch (UnsupportedEncodingException | JSONException e) {
                    cb.onFailure(statusCode, e);
                    return;
                }

                cb.onSuccess(self);
            }
        });
    }

    public OpenHABSitemap get(int i) {
        return sitemaps.get(i);
    }

    public ArrayList<OpenHABSitemap> asList() {
        return new ArrayList<>(sitemaps);
    }

    public void showSelectSitemapDialog(final Context context, final SitemapSelectCallback cb) {
        Log.d(TAG, "Opening sitemap selection dialog");
        final List<String> sitemapNameList = new ArrayList<>();
        for (int i = 0; i < sitemaps.size(); i++) {
            sitemapNameList.add(sitemaps.get(i).getName());
        }
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(context.getString(R.string.mainmenu_openhab_selectsitemap));
        try {
            selectSitemapDialog = dialogBuilder.setItems(sitemapNameList.toArray(new CharSequence[sitemapNameList.size()]),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            Log.d(TAG, "Selected sitemap " + sitemapNameList.get(item));
                            SharedPreferences settings =
                                    PreferenceManager.getDefaultSharedPreferences(context);
                            SharedPreferences.Editor preferencesEditor = settings.edit();
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP, get(item).getName());
                            preferencesEditor.apply();
                            cb.onSuccess(sitemaps.get(item));
                        }
                    }).show();
        } catch (WindowManager.BadTokenException e) {
            cb.onFailure(e);
        }
    }

    public void dismiss() {
        if (selectSitemapDialog != null && selectSitemapDialog.isShowing()) {
            selectSitemapDialog.dismiss();
        }
    }

    public interface SitemapLoadCallback {
        void onFailure(int statusCode, Throwable error);
        void onSuccess(SitemapList list);
    }

    public interface SitemapSelectCallback {
        void onFailure(Throwable error);
        void onSuccess(OpenHABSitemap selectedSitemap);
    }
}
