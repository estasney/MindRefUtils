package org.estasney.android;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
     * Pad the activity's content view by the current insets and fill the exposed edges.
     * <p>
     * Padding the content view reveals whatever sits behind it, which is the theme's window
     * background rather than anything the app drew. Painting the content view keeps those
     * edges consistent with the caller's own background. System bar icons are switched to
     * whichever contrast reads against that same color, so the caller supplies one value
     * rather than a color and a matching flag that could disagree.
     * <p>
     * Safe to call from any thread, and safe to call more than once.
     *
     * @param activity        - Activity whose content view should be padded
     * @param backgroundColor - ARGB color painted behind the system bars
     */
    public static void applyToContentView(Activity activity, int backgroundColor) {
        activity.runOnUiThread(() -> {
            View content = activity.findViewById(android.R.id.content);
            if (content == null) {
                Log.e(TAG, "applyToContentView - no content view found");
                return;
            }
            
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(backgroundColor));
            content.setBackgroundColor(backgroundColor);

            boolean backgroundIsLight = ColorUtils.calculateLuminance(backgroundColor) > 0.5;
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(activity.getWindow(), content);
            controller.setAppearanceLightStatusBars(backgroundIsLight);
            controller.setAppearanceLightNavigationBars(backgroundIsLight);
            Log.d(TAG, "applyToContentView - background " + Integer.toHexString(backgroundColor)
                    + ", light bars " + backgroundIsLight);

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
