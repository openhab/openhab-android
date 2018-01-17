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
import org.openhab.habdroid.util.Util;

public class OpenHABAboutActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            AboutMainFragment f = new AboutMainFragment();
            f.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.about_container, f)
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

            mPagerAdapter = new AboutPagerAdapter(getChildFragmentManager(),
                    getActivity(), getArguments());
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
        private Bundle mExtras;

        AboutPagerAdapter(FragmentManager fm, Context context, Bundle extras) {
            super(fm);
            mContext = context;
            mExtras = extras;
        }

        @Override
        public Fragment getItem(int i) {
            switch(i) {
                default:
                    return new AboutFragment();
                case 1:
                    Fragment infoFragment = new OpenHABInfoFragment();
                    infoFragment.setArguments(new Bundle(mExtras));

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
