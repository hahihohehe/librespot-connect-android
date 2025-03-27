package de.narlt.spotifybridge;

import android.app.Application;

import xyz.gianlu.librespot.audio.decoders.Decoders;
import xyz.gianlu.librespot.audio.format.SuperAudioFormat;
import xyz.gianlu.librespot.player.decoders.AndroidNativeDecoder;

public final class LibrespotConnectApp extends Application {
    private static final String TAG = LibrespotConnectApp.class.getSimpleName();

    static {
        Decoders.registerDecoder(SuperAudioFormat.VORBIS, AndroidNativeDecoder.class);
        Decoders.registerDecoder(SuperAudioFormat.MP3, AndroidNativeDecoder.class);

    }

}
