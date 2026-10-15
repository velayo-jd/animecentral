package com.example.animecentralapp.ui.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.databinding.ItemCommentBinding;
import com.example.animecentralapp.model.Comment;
import com.example.animecentralapp.util.AdminAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comments with one level of replies. Replies are stored in the same list with a parentId and
 * shown indented under their parent. A parent with replies has a "N replies" toggle.
 * Admins can long-press any comment to hide / unhide or delete it.
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.Holder> {

    public interface OnReply { void onReply(Comment target); }
    public interface OnModerate { void onModerate(Comment target, int replyCount); }

    private static class Row {
        final Comment comment;
        final boolean isReply;
        final int replyCount;

        Row(Comment comment, boolean isReply, int replyCount) {
            this.comment = comment;
            this.isReply = isReply;
            this.replyCount = replyCount;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private List<Comment> source = new ArrayList<>();
    private OnReply onReply;
    private OnModerate onModerate;

    public void setOnReply(@Nullable OnReply onReply) {
        this.onReply = onReply;
    }

    public void setOnModerate(@Nullable OnModerate onModerate) {
        this.onModerate = onModerate;
    }

    /** Pass the flat list from Firestore (newest first). */
    public void submitComments(@Nullable List<Comment> list) {
        source = list == null ? new ArrayList<>() : list;
        rebuild();
    }

    /** Opens the replies of a comment (used right after you post one). */
    public void expand(@Nullable String commentId) {
        if (commentId == null) return;
        expanded.add(commentId);
        rebuild();
    }

    private void rebuild() {
        Map<String, List<Comment>> replies = new HashMap<>();
        List<Comment> top = new ArrayList<>();
        for (Comment c : source) {
            String p = c.getParentId();
            if (p == null || p.isEmpty()) {
                top.add(c);
            } else {
                List<Comment> g = replies.get(p);
                if (g == null) { g = new ArrayList<>(); replies.put(p, g); }
                g.add(c);
            }
        }

        rows.clear();
        for (Comment c : top) {
            List<Comment> g = replies.get(c.getCommentId());
            int count = g == null ? 0 : g.size();
            rows.add(new Row(c, false, count));
            if (count > 0 && expanded.contains(c.getCommentId())) {
                List<Comment> oldestFirst = new ArrayList<>(g);
                Collections.reverse(oldestFirst); // the list arrives newest first
                for (Comment r : oldestFirst) rows.add(new Row(r, true, 0));
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static String ago(long ts) {
        if (ts <= 0) return "";
        long minutes = (System.currentTimeMillis() - ts) / 60000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 30) return days + "d ago";
        return (days / 30) + "mo ago";
    }

    public class Holder extends RecyclerView.ViewHolder {
        private final ItemCommentBinding b;

        Holder(ItemCommentBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Row row) {
            Comment c = row.comment;
            float dp = itemView.getResources().getDisplayMetrics().density;
            boolean admin = AdminAccess.isAdmin();

            // Replies are indented and a little smaller
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) b.getRoot().getLayoutParams();
            lp.setMarginStart(row.isReply ? (int) (46 * dp) : 0);
            b.getRoot().setLayoutParams(lp);

            int avatar = (int) ((row.isReply ? 28 : 36) * dp);
            ViewGroup.LayoutParams ap = b.commentAvatarBox.getLayoutParams();
            ap.width = avatar;
            ap.height = avatar;
            b.commentAvatarBox.setLayoutParams(ap);

            String name = c.getUsername();
            if (name == null || name.isEmpty()) name = "Guest";
            b.commentUsername.setText(name);
            b.commentTime.setText(ago(c.getTimestamp()));

            // The text: normal, or marked as hidden by a moderator
            if (c.isHidden() && !admin) {
                b.commentText.setText("This comment was removed by a moderator.");
                b.commentText.setTypeface(null, Typeface.ITALIC);
                b.commentText.setAlpha(0.6f);
                b.commentReply.setVisibility(View.GONE);
            } else if (c.isHidden()) {                 // admins still see it, dimmed and marked
                b.commentText.setText("Hidden  ·  " + c.getText());
                b.commentText.setTypeface(null, Typeface.NORMAL);
                b.commentText.setAlpha(0.5f);
                b.commentReply.setVisibility(View.VISIBLE);
            } else {
                b.commentText.setText(c.getText());
                b.commentText.setTypeface(null, Typeface.NORMAL);
                b.commentText.setAlpha(1f);
                b.commentReply.setVisibility(View.VISIBLE);
            }

            // Photo, or the first letter of the name
            b.commentInitial.setText(name.substring(0, 1).toUpperCase());
            String photo = c.getUserPhotoUrl();
            if (photo != null && !photo.isEmpty()) {
                b.commentInitial.setVisibility(View.GONE);
                Glide.with(itemView.getContext()).load(photo).circleCrop()
                        .into(b.userProfilePicture);
            } else {
                Glide.with(itemView.getContext()).clear(b.userProfilePicture);
                b.userProfilePicture.setImageDrawable(null);
                b.commentInitial.setVisibility(View.VISIBLE);
            }

            b.commentReply.setOnClickListener(v -> {
                if (onReply != null) onReply.onReply(c);
            });

            // Admins: long-press for the moderation menu
            if (admin && onModerate != null) {
                itemView.setOnLongClickListener(v -> {
                    onModerate.onModerate(c, row.replyCount);
                    return true;
                });
            } else {
                itemView.setOnLongClickListener(null);
                itemView.setLongClickable(false);
            }

            if (row.replyCount > 0) {
                boolean open = expanded.contains(c.getCommentId());
                b.commentRepliesToggle.setVisibility(View.VISIBLE);
                b.commentRepliesToggle.setText(open
                        ? "Hide replies"
                        : row.replyCount + (row.replyCount == 1 ? " reply" : " replies"));
                b.commentRepliesToggle.setOnClickListener(v -> {
                    String id = c.getCommentId();
                    if (id == null) return;
                    if (!expanded.remove(id)) expanded.add(id);
                    rebuild();
                });
            } else {
                b.commentRepliesToggle.setVisibility(View.GONE);
            }
        }
    }
}