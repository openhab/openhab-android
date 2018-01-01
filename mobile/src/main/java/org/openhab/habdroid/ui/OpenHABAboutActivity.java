package org.openhab.habdroid.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.mikepenz.aboutlibraries.LibsBuilder;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABVoiceService;
import org.openhab.habdroid.util.Util;

public class OpenHABAboutActivity extends AppCompatActivity {

    private static String openHABBaseUrl;
    private static String openHABUsername;
    private static String openHABPassword;
    private static int openHABVersion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);

        Bundle extras = getIntent().getExtras();
        openHABBaseUrl = extras.getString(OpenHABVoiceService.OPENHAB_BASE_URL_EXTRA);
        openHABUsername = extras.getString("username");
        openHABPassword = extras.getString("password");
        openHABVersion = extras.getInt("openHABVersion");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.about_container, new AboutMainFragment())
                    .commit();
        }

        setResult(RESULT_OK);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }

    public static class AboutMainFragment extends Fragment {

        private final String TAG = AboutMainFragment.class.getSimpleName();

        private PagerAdapter mPagerAdapter;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.fragment_about, container, false);

            final ViewPager viewPager = (ViewPager) view.findViewById(R.id.pager);

            // don't recreate the fragments when changing tabs
            viewPager.setOffscreenPageLimit(1);

            mPagerAdapter = new AboutPagerAdapter(getChildFragmentManager(), getActivity());
            viewPager.setAdapter(mPagerAdapter);

            final TabLayout tabLayout = (TabLayout) view.findViewById(R.id.tab_layout);
            tabLayout.setupWithViewPager(viewPager);
            tabLayout.setTabsFromPagerAdapter(mPagerAdapter);
            tabLayout.setTabMode(TabLayout.MODE_FIXED);

            return view;
        }

        @Override
        public void onResume() {
            Log.d(TAG, "onResume()");
            super.onResume();
        }
    }

    public static class AboutPagerAdapter extends FragmentPagerAdapter {
        private Context mContext;

        AboutPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
            mContext = context;
        }

        @Override
        public Fragment getItem(int i) {
            switch(i) {
                default:
                    return new AboutFragment();
                case 1:
                    Bundle bundle = new Bundle();
                    bundle.putString(OpenHABVoiceService.OPENHAB_BASE_URL_EXTRA, openHABBaseUrl);
                    bundle.putString("username", openHABUsername);
                    bundle.putString("password", openHABPassword);
                    bundle.putInt("openHABVersion", openHABVersion);
                    Fragment infoFragment = new OpenHABInfoFragment();
                    infoFragment.setArguments(bundle);

                    return infoFragment;
                case 2:
                    return new LibsBuilder().supportFragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                default:
                    return mContext.getString(R.string.about_title);
                case 1:
                    return mContext.getString(R.string.title_activity_openhabinfo);
                case 2:
                    return mContext.getString(R.string.title_activity_libraries);
            }
        }
    }
}
