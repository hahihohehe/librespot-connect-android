package de.narlt.spotifybridge;

import androidx.annotation.Nullable;

import com.powerbling.librespot_android_zeroconf_server.AndroidZeroconfServer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.ref.WeakReference;

import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.player.Player;

public final class LibrespotHolder {
    private volatile static WeakReference<Session> session;
    private volatile static WeakReference<Player> player;
    private volatile static WeakReference<AndroidZeroconfServer> server;

    private LibrespotHolder() {
    }

    public static void set(@NotNull Session session) {
        LibrespotHolder.session = new WeakReference<>(session);
    }

    public static void set(@NotNull Player player) {
        LibrespotHolder.player = new WeakReference<>(player);
    }

    public static void set(@NotNull AndroidZeroconfServer server) {
        LibrespotHolder.server = new WeakReference<>(server);
    }

    public static void clear() {
        Session s = getSession();
        Player p = getPlayer();
        AndroidZeroconfServer ser = getServer();


        if (p != null || s != null) {
            new Thread(() -> {
                if (p != null) p.close();

                try {
                    if (s != null) s.close();
                } catch (IOException ignored) {
                }
            }).start();
        }


        if (ser != null) {
            try {

                ser.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        player = null;
        session = null;
        server = null;
    }

    @Nullable
    public static Session getSession() {
        return session != null ? session.get() : null;
    }

    @Nullable
    public static Player getPlayer() {
        return player != null ? player.get() : null;
    }

    @Nullable
    public static AndroidZeroconfServer getServer() {
        return server != null ? server.get() : null;
    }

    public static boolean hasSession() {
        return getSession() != null;
    }

    public static boolean hasPlayer() {
        return getPlayer() != null;
    }

    public static boolean hasServer() {
        return getServer() != null;
    }
}
