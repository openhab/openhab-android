package org.openhab.habdroid.core.connection;

import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.ui.OpenHABPreferencesActivity;

public abstract class ConnectionAvailabilityAwareActivity extends AppCompatActivity
        implements ConnectionFactory.UpdateListener {
    private static final String TAG = ConnectionAvailabilityAwareActivity.class.getSimpleName();
    public static final String NO_NETWORK_TAG = "noNetwork";
    protected MessageHandler mMessageHandler;
    private Connection mConnectionOnPause;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mMessageHandler = new MessageHandler(this);
        super.onCreate(savedInstanceState);
    }

    public Connection getConnection() {
        try {
            Connection c = ConnectionFactory.getUsableConnection();
            hideNoNetworkFragment();
            return c;
        } catch (ConnectionException e) {
            if (e instanceof NoUrlInformationException) {
                if (!isFinishing()) {
                    DialogInterface.OnClickListener clickCb = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent preferencesIntent = new Intent(
                                    ConnectionAvailabilityAwareActivity.this,
                                    OpenHABPreferencesActivity.class);
                            TaskStackBuilder.create(ConnectionAvailabilityAwareActivity.this)
                                    .addNextIntentWithParentStack(preferencesIntent)
                                    .startActivities();
                        }
                    };
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.error_no_url)
                            .setPositiveButton(R.string.go_to_settings_button, clickCb)
                            .show();
                }
            } else if (e instanceof NetworkNotSupportedException) {
                String message = getString(R.string.error_network_type_unsupported,
                        ((NetworkNotSupportedException) e).getNetworkInfo().getTypeName());
                showNoNetworkFragment(message);
            } else {
                showNoNetworkFragment(getString(R.string.error_network_not_available));
            }
        }
        return null;
    }

    private void showNoNetworkFragment(String message) {
        Fragment noNetworkFrament = new NoNetworkFragment();
        Bundle bundle = new Bundle();
        bundle.putString(NoNetworkFragment.NO_NETWORK_MESSAGE, message);
        noNetworkFrament.setArguments(bundle);

        onEnterNoNetwork();

        mMessageHandler.closeAllMessages();

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.main_content, noNetworkFrament, NO_NETWORK_TAG)
                .commit();

        setTitle(R.string.app_name);
    }

    private void hideNoNetworkFragment() {
        Fragment fragment = getFragmentManager().findFragmentByTag(NO_NETWORK_TAG);
        if (fragment != null) {
            getFragmentManager().beginTransaction().remove(fragment).commit();
            getFragmentManager().executePendingTransactions();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            onLeaveNoNetwork();
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
        ConnectionFactory.addListener(this);

        try {
            Connection c = ConnectionFactory.getUsableConnection();
            if (c != mConnectionOnPause) {
                onConnectionChanged();
            }
        } catch (ConnectionException e) {
            if (mConnectionOnPause != null) {
                onConnectionChanged();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConnectionFactory.removeListener(this);

        try {
            mConnectionOnPause = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            mConnectionOnPause = null;
        }
    }

    @Override
    public void onConnectionChanged() {
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
                    ConnectionFactory.restartNetworkCheck();
                    getActivity().recreate();
                }
            });

            return view;
        }
    }
}
