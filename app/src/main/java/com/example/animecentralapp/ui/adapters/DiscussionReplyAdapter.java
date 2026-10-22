package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.databinding.ItemDiscussionReplyBinding;
import com.example.animecentralapp.model.DiscussionReply;
import com.example.animecentralapp.util.TimeAgo;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Replies under a thread, oldest first. Spoiler replies stay hidden until tapped. */
public class DiscussionReplyAdapter
        extends ListAdapter<DiscussionReply, DiscussionReplyAdapter.Holder> {

    public interface OnReplyLongPress { void onLongPress(DiscussionReply reply); }

    private final Set<String> revealed = new HashSet<>();
    private OnReplyLongPress longPress;

    public DiscussionReplyAdapter() {
        super(DIFF);
    }

    public void setOnLongPress(@Nullable OnReplyLongPress l) { longPress = l; }

    private static final DiffUtil.ItemCallback<DiscussionReply> DIFF =
            new DiffUtil.ItemCallback<DiscussionReply>() {
                @Override
                public boolean areItemsTheSame(@NonNull DiscussionReply a, @NonNull DiscussionReply b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DiscussionReply a, @NonNull DiscussionReply b) {
                    return Objects.equals(a.getText(), b.getText())
                            && a.isSpoiler() == b.isSpoiler()
                            && Objects.equals(a.getUsername(), b.getUsername());
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDiscussionReplyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemDiscussionReplyBinding b;

        Holder(ItemDiscussionReplyBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(DiscussionReply r) {
            String name = r.getUsername() == null || r.getUsername().isEmpty()
                    ? "Guest" : r.getUsername();
            b.replyName.setText(name);
            b.replyTime.setText(TimeAgo.ago(r.getTimestamp()));
            b.replyText.setText(r.getText());
            b.replySpoilerTag.setVisibility(r.isSpoiler() ? View.VISIBLE : View.GONE);

            // Photo, or the first letter of the name
            b.replyInitial.setText(name.substring(0, 1).toUpperCase());
            String photo = r.getUserPhotoUrl();
            if (photo != null && !photo.isEmpty()) {
                b.replyInitial.setVisibility(View.GONE);
                Glide.with(itemView.getContext()).load(photo).circleCrop().into(b.replyPhoto);
            } else {
                Glide.with(itemView.getContext()).clear(b.replyPhoto);
                b.replyPhoto.setImageDrawable(null);
                b.replyInitial.setVisibility(View.VISIBLE);
            }

            boolean hidden = r.isSpoiler() && r.getId() != null && !revealed.contains(r.getId());
            b.replyText.setVisibility(hidden ? View.GONE : View.VISIBLE);
            b.replyCover.setVisibility(hidden ? View.VISIBLE : View.GONE);
            b.replyCover.setOnClickListener(v -> {
                if (r.getId() == null) return;
                revealed.add(r.getId());
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });

            if (longPress != null) {
                itemView.setOnLongClickListener(v -> {
                    longPress.onLongPress(r);
                    return true;
                });
            } else {
                itemView.setOnLongClickListener(null);
            }
        }
    }
}