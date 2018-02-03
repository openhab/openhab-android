package org.openhab.habdroid.core.connection;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
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
import org.openhab.habdroid.ui.OpenHABPreferencesActivity;

import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_EXTRA;
import static org.openhab.habdroid.ui.OpenHABPreferencesActivity.NO_URL_INFO_EXCEPTION_MESSAGE;

public abstract class ConnectionAvailabilityAwareActivity extends AppCompatActivity {
    private static final String TAG = ConnectionAvailabilityAwareActivity.class.getSimpleName();
    public static final String NO_NETWORK_TAG = "noNetwork";

    private final ConnectionChangeListener mConnectionChangeListener = new ConnectionChangeListener();

    public Connection getConnection(int connectionType) {
        try {
            return ConnectionFactory.getConnection(connectionType);
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

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mConnectionChangeListener,
                new IntentFilter(ConnectionFactory.NETWORK_CHANGED));

        Fragment fragment = getFragmentManager().findFragmentByTag(NO_NETWORK_TAG);
        if (fragment == null || !fragment.isVisible()) {
            return;
        }
        try {
            ConnectionFactory.getConnection(Connection.TYPE_ANY);
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

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mConnectionChangeListener);
    }

    public void onConnectivityChanged() {
        ConnectionFactory.getInstance().cachedConnections.clear();
    }

    public class ConnectionChangeListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onConnectivityChanged();
        }
    }

    public static class NoNetworkFragment extends Fragment {
        public static final String NO_NETWORK_MESSAGE = "message";

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Bundle arguments = getArguments();

            View view = inflater.inflate(R.layout.fragment_no_network, container, false);

            TextView descriptionText = view.findViewById(R.id.network_error_description);
            String message = arguments.getString(NO_NETWORK_MESSAGE);
            if (!TextUtils.isEmpty(message)) {
                descriptionText.setText(message);
            } else {
                descriptionText.setVisibility(View.GONE);
            }

            final ImageView watermark = view.findViewById(R.id.network_error_image);

            Drawable errorImage = getResources().getDrawable(R.drawable.ic_signal_cellular_off_black_24dp);
            errorImage.setColorFilter(
                    ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN);
            watermark.setImageDrawable(errorImage);

            final Button restartButton = view.findViewById(R.id.network_error_try_again);
            restartButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getActivity().recreate();
                }
            });

            return view;
        }
    }
}
