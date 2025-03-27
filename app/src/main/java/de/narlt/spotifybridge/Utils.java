package de.narlt.spotifybridge;
import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class Utils {

    private Utils() {
    }

    @NotNull
    public static String getText(@NotNull TextInputLayout layout) {
        EditText editText = layout.getEditText();
        if (editText == null) throw new IllegalStateException();
        return editText.getText().toString();
    }

    @NotNull
    public static File getCredentialsFile(@NotNull Context context) {
        return new File(context.getCacheDir(), "credentials.json");
    }

    @SuppressLint("DefaultLocale")
    public static String formatTimeString(int time) {
        int sec = (time / 1000) % 60;
        int mins = ((time / 1000) - sec) / 60;

        return String.format("%d:%02d", mins, sec);
    }
}
