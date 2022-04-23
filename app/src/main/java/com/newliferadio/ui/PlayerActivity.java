package com.newliferadio.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.newliferadio.OnPlayerUpdate;
import com.newliferadio.PlayerBinder;
import com.newliferadio.PlayerService;
import com.newliferadio.R;

public class PlayerActivity extends AppCompatActivity implements OnPlayerUpdate {

    private static final int PERMISSION_READ_STATE = 89;
    public final static String WEB_LINK = "http://nlradio.net";
    private final String MAIL = "nlrnetwork@gmail.com";

    private ToggleButton btnPlay;
    private TextView tvMeta;

    private long back_pressed;
    private PlayerService mPlayerService;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            PlayerBinder binder = (PlayerBinder) service;
            mPlayerService = binder.getService();
            mPlayerService.setOnPlayerUpdate(PlayerActivity.this);

            if (ContextCompat.checkSelfPermission(
                    PlayerActivity.this,
                    Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                        PlayerActivity.this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSION_READ_STATE
                );
            } else if (mPlayerService != null) {
                mPlayerService.attachPhoneListener();
            }

            if (mPlayerService != null && mPlayerService.isRadioPlaying()) {
                onPlayService();
                tvMeta.setText(mPlayerService.getTitleInApp());
            }
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mPlayerService = null;
        }
    };

    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stop();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mNoisyReceiver, filter);

        btnPlay = findViewById(R.id.btnPlay);
        tvMeta = findViewById(R.id.tvMeta);

        initSocialBtn();

        ToggleButton btnGoodQuality = findViewById(R.id.btnGoodQuality);
        ToggleButton btnHighQuality = findViewById(R.id.btnHighQuality);
        btnGoodQuality.setOnClickListener(v -> {
            if (mPlayerService != null) mPlayerService.setGoodQuality();
            btnHighQuality.setChecked(false);
            btnHighQuality.setEnabled(true);
            btnGoodQuality.setEnabled(false);
        });
        btnHighQuality.setOnClickListener(v -> {
            if (mPlayerService != null) mPlayerService.setHighQuality();
            btnGoodQuality.setChecked(false);
            btnGoodQuality.setEnabled(true);
            btnHighQuality.setEnabled(false);
        });

        btnPlay.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) start();
            else stop();
        });

        bindService(new Intent(this, PlayerService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_READ_STATE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mPlayerService != null) {
                    mPlayerService.attachPhoneListener();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (back_pressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            showToast(getString(R.string.back_pressed_text));
            back_pressed = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayerService != null) {
            mPlayerService.stopForeground();
        }
        unbindService(serviceConnection);
        unregisterReceiver(mNoisyReceiver);
    }

    @UiThread
    private void start() {
        if (mPlayerService != null) {
            tvMeta.setText(R.string.loading_text);
            mPlayerService.startForeground();
            mPlayerService.start();
        }
    }

    @UiThread
    private void stop() {
        if (mPlayerService != null) {
            tvMeta.setText(null);
            mPlayerService.stop(false);
        }
    }

    @Override
    public void onPlayService() {
        tvMeta.setVisibility(View.VISIBLE);
    }

    @Override
    public void onTitleService(String title) {
        tvMeta.setText(title);
    }

    @Override
    public void onStopService() {
        tvMeta.setVisibility(View.GONE);
    }

    @Override
    public void onErrorService(String error) {
        btnPlay.setChecked(false);
        new AlertDialog.Builder(PlayerActivity.this)
                .setTitle(R.string.error)
                .setMessage(error)
                .setPositiveButton(R.string.settings, (dialog, id) -> {
                    Intent intent = new Intent();
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setAction(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.close, (dialog, id) -> dialog.cancel())
                .show();
    }

    private void initSocialBtn() {
        findViewById(R.id.ivWeb).setOnClickListener(v -> {
            Intent intent = new Intent(PlayerActivity.this, WebActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_out, R.anim.hold);
        });
        findViewById(R.id.ivShare).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.radio_new_life));
            intent.putExtra(Intent.EXTRA_TEXT, "https://nlradio.net/skachat-prilozhenie/");
            intent.setType("text/plain");
            startActivity(intent);
        });
        findViewById(R.id.ivYoutube).setOnClickListener(v -> {
            try {
                Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/channel/UCS3i9Ua6qop7iwO6WgbZWTQ"));
                startActivity(email);
            } catch (Exception e) {
                e.printStackTrace();
                showToast(getString(R.string.app_not_instaled, "YouTube"));
            }
        });
        findViewById(R.id.ivViber).setOnClickListener(v -> {
            try {
                Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("https://invite.viber.com/?g2=AQBCTxA6B%2BmUXEp7sFGDAH12acqo3xYh1b%2FFXiuPecnNrb3VAGbw3CaL%2BZvAnMvp"));
                startActivity(email);
            } catch (Exception e) {
                e.printStackTrace();
                showToast(getString(R.string.app_not_instaled, "Viber"));
            }
        });
        findViewById(R.id.ivTelegram).setOnClickListener(v -> {
            Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/joinchat/JmXPcVayF2lJce00SNP9qg"));
            startActivity(email);
        });
        findViewById(R.id.ivWhatsapp).setOnClickListener(v -> {
            try {
                PackageManager pm = getPackageManager();
                pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://api.whatsapp.com/send?phone=+79112413777"));
                startActivity(i);
            } catch (Exception e) {
                e.printStackTrace();
                showToast(getString(R.string.app_not_instaled, "WhatsApp"));
            }
        });

        findViewById(R.id.ivEmail).setOnClickListener(v -> {
            Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:" + MAIL));
            startActivity(email);
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}