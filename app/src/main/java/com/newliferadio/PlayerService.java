package com.newliferadio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.newliferadio.ui.ApiService;
import com.newliferadio.ui.NotificationHelperActivity;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerService extends Service implements Player.EventListener {

    public static final String START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION";
    public static final String STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION";

    private static final String CMD_NAME = "command";
    private static final String CMD_STOP = "pause";
    private static String SERVICE_CMD = "com.sec.android.app.music.musicservicecommand";
    private final String STREAM_URL = "http://ic2.christiannetcast.com/nlradio";

    private final int NOTIFY_ID = 256123;
    private NotificationManager mNotificationManager;
    private PlayerStatus playerStatus = PlayerStatus.NONE;
    private boolean isPlayerStarted = false;

    private Handler uiHandler = new Handler();

    private SimpleExoPlayer exoPlayer;
    private TrackSelector trackSelector;
    private MediaSource mediaSource;
    private ApiService apiService;
    private OnPlayerUpdate mOnPlayerUpdate;
    private String mTitleInApp = "";

    // Create a new PhoneStateListener
    private PhoneStateListener listener = new PhoneStateListener() {
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                if (isRadioPlaying()) {
                    stop(true);
                    playerStatus = PlayerStatus.CALL;
                }
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                if (!isRadioPlaying() && (playerStatus == PlayerStatus.CALL || isPlayerStarted)) {
                    start();
                }
            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                if (isRadioPlaying()) {
                    stop(true);
                    playerStatus = PlayerStatus.CALL;
                }
            }
            super.onCallStateChanged(state, incomingNumber);
        }
    };

    public void setOnPlayerUpdate(OnPlayerUpdate onPlayerUpdate) {
        mOnPlayerUpdate = onPlayerUpdate;
    }

    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!isRadioPlaying() && isPlayerStarted && playerStatus != PlayerStatus.CALL) {
                        start();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (isRadioPlaying()) {
                        stop(false);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (isRadioPlaying()) {
                        stop(true);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    if (isRadioPlaying()) {
                        stop(true);
                    }
                    break;
                default:
            }
        }
    };
    private boolean mAudioFocusGranted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        apiService = new ApiService();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String channelId = "notification";
            CharSequence channelName = "Уведомления";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            notificationChannel.setShowBadge(false);
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
        prepareExoPlayerFromURL();
    }

    public String getTitleInApp() {
        return mTitleInApp;
    }

    public void startForeground() {
        Intent notificationIntent = new Intent(this, NotificationHelperActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "notification");

        builder.setContentIntent(contentIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Вера, надежда, любовь");

        android.app.Notification n = builder.build();

        startForeground(NOTIFY_ID, n);
    }

    public void stopForeground() {
        stopForeground(true);
        stopSelf();
    }

    public void start() {
        playerStatus = PlayerStatus.LOADING;

        if (!mAudioFocusGranted && requestAudioFocus()) {
            forceMusicStop();
        }
        prepareExoPlayerFromURL();
        exoPlayer.setPlayWhenReady(true);
        isPlayerStarted = true;
    }

    public void stop(boolean isPause) {
        mTitleInApp = "";
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.release();
            exoPlayer = null;
            if (!isPause) {
                isPlayerStarted = false;
                mAudioFocusGranted = false;
            }
        }
        uiHandler.removeCallbacks(mRunnable);
    }

    public void attachPhoneListener() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void prepareExoPlayerFromURL() {
        if (exoPlayer == null) {
            BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
            DefaultBandwidthMeter defaultBandwidthMeter = new DefaultBandwidthMeter();
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                    Util.getUserAgent(this, getString(R.string.app_name)), defaultBandwidthMeter);
            trackSelector = new DefaultTrackSelector(trackSelectionFactory);

            mediaSource = new ExtractorMediaSource(Uri.parse(STREAM_URL), dataSourceFactory, extractorsFactory, null, null);
        }
        exoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        exoPlayer.addListener(this);

        exoPlayer.prepare(mediaSource);
    }

    private void notification(String title) {
        Intent notificationIntent = new Intent(this, NotificationHelperActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "notification");

        builder.setContentIntent(contentIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("В эфире: " + title);

        android.app.Notification n = builder.build();

        mNotificationManager.notify(NOTIFY_ID, n);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, final int playbackState) {
        if (playWhenReady) {
            playerStatus = PlayerStatus.PLAY;
            if (mOnPlayerUpdate != null) {
                mOnPlayerUpdate.onPlayService();
            }

            if (playbackState == Player.STATE_READY) {
                updateSubtitle();
            }
        } else {
            if (mOnPlayerUpdate != null) {
                mOnPlayerUpdate.onStopService();
            }
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (mOnPlayerUpdate != null) {
            mOnPlayerUpdate.onErrorService(error.getMessage());
        }
        stop(false);
    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            updateSubtitle();
        }
    };

    private void updateSubtitle() {
        apiService.getService().getTrackName().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String content = response.body().string();
                        if (!content.equals(mTitleInApp)) {
                            mTitleInApp = content;
                            if (mOnPlayerUpdate != null) {
                                mOnPlayerUpdate.onTitleService(content);
                            }
                            if (playerStatus == PlayerStatus.PLAY) {
                                notification(content);
                            }
                        }
                        uiHandler.postDelayed(mRunnable, 5000);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {

            }
        });
    }

    private boolean requestAudioFocus() {
        if (!mAudioFocusGranted) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            // Request audio focus for play back
            int result = am.requestAudioFocus(mOnAudioFocusChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            mAudioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return mAudioFocusGranted;
    }

    private void forceMusicStop() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.isMusicActive()) {
            Intent intentToStop = new Intent(SERVICE_CMD);
            intentToStop.putExtra(CMD_NAME, CMD_STOP);
            sendBroadcast(intentToStop);
        }
    }

    public boolean isRadioPlaying() {
        return exoPlayer != null && exoPlayer.getPlayWhenReady();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public class PlayerBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerBinder();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stop(false);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop(false);
    }

    public interface OnPlayerUpdate {
        void onPlayService();

        void onTitleService(String content);

        void onStopService();

        void onErrorService(String error);
    }
}
