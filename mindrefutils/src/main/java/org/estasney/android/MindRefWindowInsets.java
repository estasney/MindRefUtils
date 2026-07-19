package org.estasney.android;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the activity's content clear of the system bars.
 * <p>
 * Android 15 lays a window targeting SDK 35 out edge to edge, drawing the status bar,
 * navigation bar and any display cutout over the app's content. Padding the content view
 * shrinks the SDL surface to the safe area, so the UI toolkit above it needs no knowledge
 * of insets.
 *
 * @noinspection unused
 */
public class MindRefWindowInsets {
    private static final String TAG = "mindrefutils";

    /**
     * Install a listener that pads the activity's content view by the current insets.
     * <p>
     * Safe to call from any thread, and safe to call more than once - the listener
     * replaces any previously installed one.
     *
     * @param activity - Activity whose content view should be padded
     */
    public static void applyToContentView(Activity activity) {
        activity.runOnUiThread(() -> {
            View content = activity.findViewById(android.R.id.content);
            if (content == null) {
                Log.e(TAG, "applyToContentView - no content view found");
                return;
            }

            ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
                Insets safeArea = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                Log.d(TAG, "applyToContentView - padding ["
                        + safeArea.left + "," + safeArea.top + "]["
                        + safeArea.right + "," + safeArea.bottom + "]");
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
                // Passed through rather than consumed so SDL still sees IME insets.
                return windowInsets;
            });

            ViewCompat.requestApplyInsets(content);
        });
    }
}
