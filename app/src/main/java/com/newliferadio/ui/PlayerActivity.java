package com.newliferadio.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.newliferadio.PlayerService;
import com.newliferadio.R;

public class PlayerActivity extends AppCompatActivity implements PlayerService.OnPlayerUpdate {

    private static final int PERMISSION_READ_STATE = 89;
    public final static String WEB_LINK = "http://nlradio.net";
    private final String PHONE_NUMBER = "+79112413777";
    private final String MAIL = "nlrnetwork@gmail.com";

    private ImageButton btnPlay;
    private ImageButton btnStop;
    private TextView metaTitle;

    private long back_pressed;
    private PlayerService mPlayerService;

    private ServiceConnection myConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
            mPlayerService = binder.getService();
            mPlayerService.setOnPlayerUpdate(PlayerActivity.this);

            if (ContextCompat.checkSelfPermission(PlayerActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(PlayerActivity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_READ_STATE);
            } else {
                if (mPlayerService != null) {
                    mPlayerService.attachPhoneListener();
                }
            }

            if (mPlayerService.isRadioPlaying()) {
                onPlayService();
                metaTitle.setText(mPlayerService.getTitleInApp());
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


        btnPlay = findViewById(R.id.play);
        btnStop = findViewById(R.id.stop);
        metaTitle = findViewById(R.id.title);

        findViewById(R.id.web).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PlayerActivity.this, WebActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_out, R.anim.hold);
            }
        });
        findViewById(R.id.viber).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("https://invite.viber.com/?g2=AQBsrLk1Z4WkEEp7sFHZUdDvt6juSbo1MYJsysnRsNzc%2Byk6bYr4pn3U4gwOYIbg"));
                    startActivity(email);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(PlayerActivity.this, "Приложение Viber не установлено!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.telegram).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/joinchat/JmXPcVayF2lJce00SNP9qg"));
                startActivity(email);
            }
        });
        findViewById(R.id.whatsapp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    PackageManager pm = getPackageManager();
                    pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("https://api.whatsapp.com/send?phone=+79112413777"));
                    startActivity(i);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(PlayerActivity.this, "Приложение WhatsApp не установлено!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.email).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent email = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:" + MAIL));
                startActivity(email);
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        bindService(new Intent(this, PlayerService.class), myConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_READ_STATE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mPlayerService != null) {
                        mPlayerService.attachPhoneListener();
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (back_pressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "Нажмите еще раз для выхода!", Toast.LENGTH_SHORT).show();
            back_pressed = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayerService != null) {
            mPlayerService.stopForeground();
        }
        unbindService(myConnection);
        unregisterReceiver(mNoisyReceiver);
    }

    @UiThread
    private void start() {
        if (mPlayerService != null) {
            metaTitle.setText("...");
            btnPlay.setEnabled(false);
            mPlayerService.startForeground();
            mPlayerService.start();
        }
    }

    @UiThread
    private void stop() {
        if (mPlayerService != null) {
            btnStop.setEnabled(false);
            metaTitle.setText(null);
            mPlayerService.stop(false);
        }
    }

    @Override
    public void onPlayService() {
        btnPlay.setEnabled(false);
        btnStop.setEnabled(true);
        btnPlay.setVisibility(View.INVISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        metaTitle.setVisibility(View.VISIBLE);
    }

    @Override
    public void onTitleService(String title) {
        metaTitle.setText(title);
    }

    @Override
    public void onStopService() {
        btnPlay.setEnabled(true);
        btnStop.setEnabled(false);
        btnPlay.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.INVISIBLE);
        metaTitle.setVisibility(View.GONE);
    }

    @Override
    public void onErrorService(String error) {
        btnPlay.setEnabled(true);
        new AlertDialog.Builder(PlayerActivity.this)
                .setTitle("Ошибка")
                .setMessage(error)
                .setPositiveButton("Настройки",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent();
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.setAction(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                                startActivity(intent);
                            }
                        }
                )
                .setNegativeButton("Закрыть",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        }
                )
                .show();
    }
}