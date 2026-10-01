package com.example.guitartuner.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.guitartuner.R;

/** Небольшие анимации интерфейса. */
public final class Anim {

    private static final long CASCADE_STEP_MS = 55;
    private static final long CASCADE_DURATION_MS = 420;

    private Anim() {
    }

    /** Блоки экрана появляются по очереди: снизу вверх с проявлением. */
    public static void cascadeIn(ViewGroup container) {
        if (container == null) return;
        float shift = 28 * container.getResources().getDisplayMetrics().density;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(shift);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * CASCADE_STEP_MS)
                    .setDuration(CASCADE_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    /** "Подскок" при смене значения: ноты, темпа, счёта. */
    public static void pop(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1.18f);
        view.setScaleY(1.18f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(0)
                .setDuration(260)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();
    }

    /** Открыть подробный экран (урок, песню) с выездом справа; "назад" — обратно. */
    public static void openDetail(FragmentActivity activity, Fragment fragment) {
        activity.getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.detail_enter, R.anim.detail_exit,
                        R.anim.detail_pop_enter, R.anim.detail_pop_exit)
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
}
