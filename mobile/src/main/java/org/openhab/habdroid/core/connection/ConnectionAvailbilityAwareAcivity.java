package org.openhab.habdroid.core.connection;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.ui.OpenHABPreferencesActivity;

import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_EXTRA;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_MESSAGE;

public abstract class ConnectionAvailbilityAwareAcivity extends AppCompatActivity {
    private static final String TAG = ConnectionAvailbilityAwareAcivity.class.getSimpleName();

    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(ConnectionFactory.getInstance());
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Tried to unregister not registered BroadcastReceiver.", e);
        }
    }

    public Connection getConnection(int connectionType) {
        try {
            return ConnectionFactory.getConnection(connectionType, this);
        } catch (NetworkNotAvailableException | NetworkNotSupportedException e) {
            showNoNetworkFragment(e.getMessage());
        } catch (NoUrlInformationException e) {
            Intent preferencesIntent = new Intent(this, OpenHABPreferencesActivity.class);
            preferencesIntent.putExtra(NO_URL_INFO_EXCEPTION_EXTRA, true);
            preferencesIntent.putExtra(NO_URL_INFO_EXCEPTION_MESSAGE, e.getMessage());

            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(preferencesIntent)
                    .startActivities();
        }
        return null;
    }

    private void showNoNetworkFragment(String message) {
        Fragment noNetworkFrament = new NoNetworkFragment();
        Bundle bundle = new Bundle();
        bundle.putString(NoNetworkFragment.NO_NETWORK_MESSAGE, message);
        noNetworkFrament.setArguments(bundle);

        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, noNetworkFrament)
                .commit();

        Toolbar noNetworkToolbar = findViewById(R.id.openhab_toolbar_no_network);
        setSupportActionBar(noNetworkToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    private void restartApp() {
        Intent startActivity = new Intent(this, OpenHABMainActivity.class);
        startActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(startActivity);

        System.exit(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ConnectionFactory.getConnection(Connections.ANY, this);
            restartApp();
        } catch (ConnectionException e) {
            Log.d(TAG, "After resuming the app, there's still no network available.", e);
        }
    }

    public static class NoNetworkFragment extends Fragment {
        private static final String TAG = NoNetworkFragment.class.getSimpleName();
        public static final String NO_NETWORK_MESSAGE = "message";

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Bundle arguments = getArguments();

            View view = inflater.inflate(R.layout.fragment_no_network, container, false);

            TextView descriptionText = view.findViewById(R.id.network_error_description);
            if (!arguments.getString(NO_NETWORK_MESSAGE, "").isEmpty()) {
                descriptionText.setText(arguments.getString(NO_NETWORK_MESSAGE));
            } else {
                descriptionText.setVisibility(View.GONE);
            }
            final ImageView watermark = view.findViewById(R.id.network_error_image);

            Drawable errorImage = getResources().getDrawable(R.drawable.ic_signal_cellular_off_black_24dp);
            errorImage.setColorFilter(
                    ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN);
            watermark.setImageDrawable(errorImage);

            final Button restartButton = view.findViewById(R.id.network_error_restart);
            restartButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((ConnectionAvailbilityAwareAcivity) getActivity()).restartApp();
                }
            });

            return view;
        }
    }
}
