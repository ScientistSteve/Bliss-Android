package net.kdt.pojavlaunch.ui;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.ImageButton;

import net.kdt.pojavlaunch.R;

public final class UiAnimationUtils {
    private static final float PRESSED_SCALE = 0.95f;
    private static final float RELEASED_SCALE = 1f;
    private static final long ANIMATION_DURATION_MS = 100L;
    private static final Interpolator EASE_IN_OUT = new AccelerateDecelerateInterpolator();

    private UiAnimationUtils() {}

    public static void installButtonPressAnimations(View root) {
        if(root == null) return;
        applyButtonPressAnimations(root);
    }

    private static void applyButtonPressAnimations(View view) {
        if (view instanceof Button || view instanceof ImageButton) {
            installButtonPressAnimation(view);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            applyButtonPressAnimations(viewGroup.getChildAt(i));
        }
    }

    private static void installButtonPressAnimation(View button) {
        if (Boolean.TRUE.equals(button.getTag(R.id.tag_button_press_animation))) return;
        button.setTag(R.id.tag_button_press_animation, true);
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    animateScale(v, PRESSED_SCALE);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_OUTSIDE:
                    animateScale(v, RELEASED_SCALE);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void animateScale(View view, float scale) {
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(ANIMATION_DURATION_MS)
                .setInterpolator(EASE_IN_OUT)
                .start();
    }
}
