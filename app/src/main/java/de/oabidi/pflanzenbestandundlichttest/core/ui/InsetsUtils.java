package de.oabidi.pflanzenbestandundlichttest.core.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Utility methods to make views respond to window insets.
 */
public final class InsetsUtils {
    private InsetsUtils() {
    }

    /**
     * Requests insets once the view is attached to the window.
     */
    public static void requestApplyInsetsWhenAttached(@NonNull View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    // no-op
                }
            });
        }
    }

    /**
     * Applies system window insets as additional padding.
     */
    public static void applySystemWindowInsetsPadding(@NonNull View view,
                                                      boolean applyLeft,
                                                      boolean applyTop,
                                                      boolean applyRight,
                                                      boolean applyBottom) {
        final int paddingStart = ViewCompat.getPaddingStart(view);
        final int paddingTop = view.getPaddingTop();
        final int paddingEnd = ViewCompat.getPaddingEnd(view);
        final int paddingBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewCompat.setPaddingRelative(
                v,
                applyLeft ? paddingStart + systemBars.left : paddingStart,
                applyTop ? paddingTop + systemBars.top : paddingTop,
                applyRight ? paddingEnd + systemBars.right : paddingEnd,
                applyBottom ? paddingBottom + systemBars.bottom : paddingBottom
            );
            return insets;
        });
        requestApplyInsetsWhenAttached(view);
    }

    /**
     * Applies system window insets as additional margins.
     */
    public static void applySystemWindowInsetsMargin(@NonNull View view,
                                                     boolean applyLeft,
                                                     boolean applyTop,
                                                     boolean applyRight,
                                                     boolean applyBottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            requestApplyInsetsWhenAttached(view);
            return;
        }
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        final int marginStart = MarginLayoutParamsCompat.getMarginStart(marginParams);
        final int marginTop = marginParams.topMargin;
        final int marginEnd = MarginLayoutParamsCompat.getMarginEnd(marginParams);
        final int marginBottom = marginParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            MarginLayoutParamsCompat.setMarginStart(lp, applyLeft ? marginStart + systemBars.left : marginStart);
            lp.topMargin = applyTop ? marginTop + systemBars.top : marginTop;
            MarginLayoutParamsCompat.setMarginEnd(lp, applyRight ? marginEnd + systemBars.right : marginEnd);
            lp.bottomMargin = applyBottom ? marginBottom + systemBars.bottom : marginBottom;
            v.setLayoutParams(lp);
            return insets;
        });
        requestApplyInsetsWhenAttached(view);
    }
}
