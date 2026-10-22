package com.example.animecentralapp.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.animecentralapp.databinding.SheetNewDiscussionBinding;
import com.example.animecentralapp.model.Discussion;
import com.example.animecentralapp.repository.DiscussionRepository;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** The "Start a discussion" bottom sheet. */
public final class NewDiscussionSheet {
    private NewDiscussionSheet() {}

    public static void show(Context ctx, String animeId, @Nullable Runnable onPosted) {
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        SheetNewDiscussionBinding b = SheetNewDiscussionBinding.inflate(LayoutInflater.from(ctx));
        dialog.setContentView(b.getRoot());

        boolean bug = DiscussionRepository.BUGS.equals(animeId);
        if (bug) {
            b.newThreadHeading.setText("Report a bug");
            b.newThreadTitle.setHint("What went wrong? (short)");
            b.newThreadBody.setHint("What did you do, what did you expect, and what happened instead?");
            b.newThreadSpoilerRow.setVisibility(View.GONE);
            b.newThreadPostBtn.setText("Send report");
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        DiscussionRepository repo = new DiscussionRepository();
        b.newThreadPostBtn.setOnClickListener(v -> {
            String title = b.newThreadTitle.getText().toString().trim();
            String body = b.newThreadBody.getText().toString().trim();
            if (title.isEmpty()) {
                b.newThreadTitle.setError("Give it a title");
                return;
            }
            Discussion thread = DiscussionRepository.newThread(
                    ctx, animeId, title, body, b.newThreadSpoiler.isChecked());
            if (bug) {
                if (body.isEmpty()) {
                    b.newThreadBody.setError("Describe what happened");
                    return;
                }
                // Handy for debugging: which build and phone this came from
                String version = "?";
                try {
                    version = ctx.getPackageManager()
                            .getPackageInfo(ctx.getPackageName(), 0).versionName;
                } catch (Exception ignored) {}
                body += "\n\n[App v" + version + "  ·  Android " + android.os.Build.VERSION.RELEASE
                        + "  ·  " + android.os.Build.MODEL + "]";
            }
            if (thread == null) {
                Toast.makeText(ctx, "Sign in to start a discussion", Toast.LENGTH_SHORT).show();
                return;
            }
            b.newThreadPostBtn.setEnabled(false);
            b.newThreadPostBtn.setAlpha(0.5f);
            repo.addThread(thread, (ok, error) -> {
                if (ok) {
                    dialog.dismiss();
                    Toast.makeText(ctx, "Posted", Toast.LENGTH_SHORT).show();
                    if (onPosted != null) onPosted.run();
                } else {
                    b.newThreadPostBtn.setEnabled(true);
                    b.newThreadPostBtn.setAlpha(1f);
                    Toast.makeText(ctx, "Couldn't post: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}