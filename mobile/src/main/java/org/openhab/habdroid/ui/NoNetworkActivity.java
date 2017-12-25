package org.openhab.habdroid.ui;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Util;

public class NoNetworkActivity extends AppCompatActivity {
    public static final String NO_NETWORK_MESSAGE = "message";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_no_network);

        TextView descriptionText = findViewById(R.id.network_error_description);
        if (getIntent().hasExtra(NO_NETWORK_MESSAGE)) {
            descriptionText.setText(getIntent().getStringExtra(NO_NETWORK_MESSAGE));
        } else {
            descriptionText.setVisibility(View.GONE);
        }
        final ImageView watermark = findViewById(R.id.network_error_image);

        Drawable errorImage = getResources().getDrawable(R.drawable.ic_signal_cellular_off_black_24dp);
        errorImage.setColorFilter(
                ContextCompat.getColor(this, R.color.empty_list_text_color),
                PorterDuff.Mode.SRC_IN);
        watermark.setImageDrawable(errorImage);

        final Button restartButton = findViewById(R.id.network_error_restart);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startActivity = new Intent(NoNetworkActivity.this, OpenHABMainActivity.class);
                startActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(startActivity);

                System.exit(0);
            }
        });

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }
}
