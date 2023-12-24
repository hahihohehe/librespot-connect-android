package xyz.gianlu.librespot.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.powerbling.librespot_android_zeroconf_server.AndroidZeroconfServer;
import com.spotify.connectstate.Connect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.gianlu.librespot.android.databinding.ActivityMainBinding;
import xyz.gianlu.librespot.android.sink.AndroidSinkOutput;
import xyz.gianlu.librespot.audio.MetadataWrapper;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.metadata.PlayableId;
import xyz.gianlu.librespot.player.Player;
import xyz.gianlu.librespot.player.PlayerConfiguration;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LibrespotHolder.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.user_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.settings_menu) {
            LibrespotHolder.clear();
            startActivity(new Intent(this, SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        binding.visibleAs.setText(pref.getString("preference_speaker_name",
                getString(R.string.app_name)));

        binding.playPauseButton.setOnClickListener((v) -> executorService.execute(new PlayPauseRunnable(() -> {
        })));

        binding.prev.setOnClickListener((v) ->
                executorService.execute(new PrevRunnable(() ->
                        Toast.makeText(this, R.string.skippedPrev, Toast.LENGTH_SHORT).show())));

        binding.next.setOnClickListener((v) ->
                executorService.execute(new NextRunnable(() ->
                        Toast.makeText(this, R.string.skippedNext, Toast.LENGTH_SHORT).show())));

        // Make seekbar immutable from user side
        binding.seekBar.setOnTouchListener(new SeekBar.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        Runnable resumeRunnable = () -> {
            binding.playPauseButton.setEnabled(true);
            binding.next.setEnabled(true);
            binding.prev.setEnabled(true);
            binding.playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        };

        Runnable pauseRunnable = () -> binding.playPauseButton.setImageResource(android.R.drawable.ic_media_play);

        Runnable closingRunnable = () -> {
            executorService.execute(new SessionClosingRunnable(() -> {
            }));

            binding.playPauseButton.setEnabled(false);
            binding.next.setEnabled(false);
            binding.prev.setEnabled(false);
            binding.songTitle.setText(R.string.noSong);
            binding.songArtist.setText(R.string.artist);
            binding.username.setText("");
            binding.connectedToLabel.setText(R.string.notConnected);
            binding.seekBar.setProgress(0);
            binding.songDuration.setText(R.string.invalidTime);
            binding.currentTime.setText(R.string.invalidTime);
            binding.songImage.setImageResource(R.drawable.defaultalbum);
        };


        Player.EventsListener playerListener = new Player.EventsListener() {
            private Runnable inactivityRunnable;
            private TimerTask timerTask;
            private final Handler handler = new Handler();
            private final Timer timer = new Timer();

            @Override
            public void onContextChanged(@NotNull Player player, @NotNull String s) {
                Log.i(TAG, "Context changed");

            }

            @Override
            public void onTrackChanged(@NotNull Player player, @NotNull PlayableId playableId, @org.jetbrains.annotations.Nullable MetadataWrapper metadataWrapper, boolean b) {

            }

            @Override
            public void onPlaybackEnded(@NotNull Player player) {
                Log.i(TAG, "Playback ended");
            }

            @Override
            public void onPlaybackPaused(@NotNull Player player, long l) {
                runOnUiThread(pauseRunnable);
            }

            @Override
            public void onPlaybackResumed(@NotNull Player player, long l) {
                if (inactivityRunnable != null) {
                    handler.removeCallbacks(inactivityRunnable);
                    inactivityRunnable = null;
                }

                runOnUiThread(resumeRunnable);

                timerTask =
                        new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(
                                        () -> {
                                            binding.seekBar.setProgress(player.time());
                                            binding.currentTime.setText(Utils.formatTimeString(player.time()));
                                        });
                            }
                        };
                timer.scheduleAtFixedRate(timerTask, 0, 500);


            }

            @Override
            public void onPlaybackFailed(@NotNull Player player, @NotNull Exception e) {
                if (e.getMessage() != null)
                    Log.e(TAG, e.getMessage());
            }

            @Override
            public void onTrackSeeked(@NotNull Player player, long l) {

            }


            @Override
            public void onMetadataAvailable(@NotNull Player player, @NotNull MetadataWrapper metadataWrapper) {
                runOnUiThread(() -> {

                    if (metadataWrapper != null) {
                        binding.songTitle.setText(metadataWrapper.getName());
                        binding.songArtist.setText(metadataWrapper.getArtist());

                        binding.seekBar.setMax(metadataWrapper.duration());
                        binding.songDuration.setText(Utils.formatTimeString(metadataWrapper.duration()));


                    } else {
                        binding.songTitle.setText(R.string.noSong);
                        binding.songArtist.setText(R.string.artist);
                    }


                });

                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        try {
                            byte[] imageData = player.currentCoverImage();

                            runOnUiThread(() -> binding.songImage.setImageBitmap(BitmapFactory
                                    .decodeByteArray(imageData, 0, imageData.length)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        return null;
                    }
                }.execute();
            }

            @Override
            public void onPlaybackHaltStateChanged(@NotNull Player player, boolean b, long l) {
                Log.i(TAG, "Playback halt state changed");

            }

            @Override
            public void onInactiveSession(@NotNull Player player, boolean b) {
                Log.i(TAG, "Inactive session");

                timerTask.cancel();

                inactivityRunnable = closingRunnable;

                // Wait one minute before closing session
                handler.postDelayed(inactivityRunnable, 20000);
            }

            @Override
            public void onVolumeChanged(@NotNull Player player, @Range(from = 0L, to = 1L) float v) {

            }

            @Override
            public void onPanicState(@NotNull Player player) {
                Log.e(TAG, "Panic State");
            }

            @Override
            public void onStartedLoading(@NotNull Player player) {
                Log.i(TAG, "Started loading");

            }

            @Override
            public void onFinishedLoading(@NotNull Player player) {
                Log.i(TAG, "Finished loading");


            }
        };

        AndroidZeroconfServer.SessionListener sessionListener = new AndroidZeroconfServer.SessionListener() {
            @Override
            public void sessionClosing(@NotNull Session session) {
                executorService.execute(new SessionClosingRunnable(() -> {
                    Toast.makeText(MainActivity.this, R.string.sessionClosing, Toast.LENGTH_SHORT).show();
                }));
                executorService.execute(closingRunnable);

            }

            @Override
            public void sessionChanged(@NotNull Session session) {
                Log.i(TAG, "Session changed");
                executorService.execute(new SessionChangedRunnable(session, new SessionChangedCallback() {

                    @Override
                    public void playerReady(@NotNull Player player, @NotNull String username) {
                        Toast.makeText(MainActivity.this, R.string.playerReady, Toast.LENGTH_SHORT).show();
                        binding.connectedToLabel.setText(R.string.connectedTo);
                        binding.username.setText(username);
                        binding.playPauseButton.setEnabled(true);
                        binding.next.setEnabled(true);
                        binding.prev.setEnabled(true);

                        player.addEventsListener(playerListener);
                    }

                    @Override
                    public void failedGettingReady(@NotNull Exception ex) {
                        Toast.makeText(MainActivity.this, R.string.somethingWentWrong, Toast.LENGTH_SHORT).show();
                    }
                }));

            }
        };


        executorService.execute(new SetupRunnable(sessionListener));

    }


    private interface SimpleCallback {
        void done();
    }

    private interface SessionChangedCallback {
        void playerReady(@NotNull Player player, @NotNull String username);

        void failedGettingReady(@NotNull Exception ex);
    }

    private static class SessionClosingRunnable implements Runnable {
        private final SimpleCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());

        SessionClosingRunnable(@NotNull SimpleCallback callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            LibrespotHolder.clear();

            handler.post(callback::done);
        }
    }

    private static class SessionChangedRunnable implements Runnable {
        private final SessionChangedCallback callback;
        private final Session session;
        private final Handler handler = new Handler(Looper.getMainLooper());

        SessionChangedRunnable(@NotNull Session session, @NotNull SessionChangedCallback callback) {
            this.callback = callback;
            this.session = session;
        }


        @Override
        public void run() {
            Log.i(TAG, "Connected to: " + session.username());

            if (LibrespotHolder.hasSession()) LibrespotHolder.clear();

            LibrespotHolder.set(session);

            Player player;
            PlayerConfiguration configuration = new PlayerConfiguration.Builder()
                    .setOutput(PlayerConfiguration.AudioOutput.CUSTOM)
                    .setOutputClass(AndroidSinkOutput.class.getName())
                    .build();

            player = new Player(configuration, session);
            LibrespotHolder.set(player);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                while (!player.isReady()) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            } else {
                try {
                    player.waitReady();
                } catch (InterruptedException ex) {
                    LibrespotHolder.clear();
                    return;
                }
            }

            handler.post(() -> callback.playerReady(player, session.username()));
        }
    }

    private class SetupRunnable implements Runnable {
        private final AndroidZeroconfServer.SessionListener sessionListener;

        SetupRunnable(@NotNull AndroidZeroconfServer.SessionListener sessionListener) {
            this.sessionListener = sessionListener;
        }

        @Override
        public void run() {

            try {
                Session.Configuration conf = new Session.Configuration.Builder()
                        .setStoreCredentials(false)
                        .setCacheEnabled(false)
                        .build();

                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                AndroidZeroconfServer.Builder builder = new AndroidZeroconfServer.Builder(getBaseContext(), conf)
                        .setPreferredLocale(Locale.getDefault().getLanguage())
                        .setDeviceType(Connect.DeviceType.SPEAKER)
                        .setDeviceId(null)
                        .setDeviceName(  // Set name as set in preferences
                                pref.getString("preference_speaker_name",
                                        getString(R.string.app_name)));

                AndroidZeroconfServer server = builder.create();

                server.addSessionListener(sessionListener);

                LibrespotHolder.set(server);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {

                    try {
                        server.closeSession();
                        server.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    private static class SeekRunnable implements Runnable {
        private final SimpleCallback callback;
        private final int pos;
        private final Handler handler = new Handler(Looper.getMainLooper());

        SeekRunnable(int pos, @NotNull SimpleCallback callback) {
            this.callback = callback;
            this.pos = pos;
        }

        @Override
        public void run() {
            Player player = LibrespotHolder.getPlayer();
            if (player == null) return;

            player.seek(pos);
            handler.post(callback::done);
        }
    }

    private static class PlayPauseRunnable implements Runnable {
        private final SimpleCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());

        PlayPauseRunnable(@NotNull SimpleCallback callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            Player player = LibrespotHolder.getPlayer();
            if (player == null) return;

            player.playPause();
            handler.post(callback::done);
        }
    }

    private static class PrevRunnable implements Runnable {
        private final SimpleCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());

        PrevRunnable(@NotNull SimpleCallback callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            Player player = LibrespotHolder.getPlayer();
            if (player == null) return;

            player.previous();
            handler.post(callback::done);
        }
    }

    private static class NextRunnable implements Runnable {
        private final SimpleCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());

        NextRunnable(@NotNull SimpleCallback callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            Player player = LibrespotHolder.getPlayer();
            if (player == null) return;

            player.next();
            handler.post(callback::done);
        }
    }
}
