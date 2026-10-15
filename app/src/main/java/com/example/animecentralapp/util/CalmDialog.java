package com.example.animecentralapp.util;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.example.animecentralapp.R;

/** The easter egg: the big logo, slowly breathing, with a quiet line at the bottom. */
public final class CalmDialog {
    private CalmDialog() {}

    public static void show(Activity a) {
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        View root = LayoutInflater.from(a).inflate(R.layout.dialog_calm, null);
        d.setContentView(root);

        View logo = root.findViewById(R.id.calm_logo);
        TextView message = root.findViewById(R.id.calm_message);
        float dp = a.getResources().getDisplayMetrics().density;

        // Logo fades and settles in, then breathes slowly
        logo.setAlpha(0f);
        logo.setScaleX(0.88f);
        logo.setScaleY(0.88f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(900).setInterpolator(new DecelerateInterpolator()).start();

        ObjectAnimator breathe = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f));
        breathe.setDuration(3200);
        breathe.setStartDelay(1000);
        breathe.setRepeatMode(ObjectAnimator.REVERSE);
        breathe.setRepeatCount(ObjectAnimator.INFINITE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.start();

        // The message arrives a moment later
        message.setAlpha(0f);
        message.setTranslationY(12 * dp);
        message.animate().alpha(1f).translationY(0f)
                .setStartDelay(1100).setDuration(1000)
                .setInterpolator(new DecelerateInterpolator()).start();

        root.setOnClickListener(v -> d.dismiss());      // tap anywhere to leave
        d.setOnDismissListener(x -> breathe.cancel());
        d.show();
    }
}
