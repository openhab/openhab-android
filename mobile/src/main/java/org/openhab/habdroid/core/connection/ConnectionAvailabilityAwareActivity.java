package org.openhab.habdroid.core.connection;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
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
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.ui.OpenHABPreferencesActivity;

import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_EXTRA;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_MESSAGE;

public abstract class ConnectionAvailabilityAwareActivity extends AppCompatActivity {
    private static final String TAG = ConnectionAvailabilityAwareActivity.class.getSimpleName();
    public static final String NO_NETWORK_TAG = "noNetwork";

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

        onEnterNoNetwork();

        MessageHandler.closeAllMessages();

        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, noNetworkFrament, NO_NETWORK_TAG)
                .commit();

        setTitle(R.string.app_name);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    /**
     * This method is called whenever this abstract class will replace the whole content with
     * other information, such as error fragments or something like that. A class extending from
     * here can override this method to also reset/clear/remove views from the activity if needed.
     */
    protected void onEnterNoNetwork() {}

    /**
     * This method is called whenever this abstract class will remove the content added when
     * onEnterNoNetwork() was called. A class extending from here can override this method to
     * re-add content or reset states.
     */
    protected void onLeaveNoNetwork() {}

    private void restartApp() {
        Intent startActivity = new Intent(this, OpenHABMainActivity.class);
        startActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(startActivity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Fragment fragment = getFragmentManager().findFragmentByTag(NO_NETWORK_TAG);
        if (fragment == null || !fragment.isVisible()) {
            return;
        }
        try {
            ConnectionFactory.getConnection(Connection.TYPE_ANY, this);
            getFragmentManager().beginTransaction().remove(fragment).commit();
            onConnectivityChanged();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            onLeaveNoNetwork();
        } catch (ConnectionException e) {
            Log.d(TAG, "After resuming the app, there's still no network available.", e);
        }
    }

    public final void onConnectivityChanged() {
        ConnectionFactory.getInstance().cachedConnections.clear();
    }

    public static class NoNetworkFragment extends Fragment {
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
                    ((ConnectionAvailabilityAwareActivity) getActivity()).restartApp();
                }
            });

            return view;
        }
    }
}
