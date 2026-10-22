package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.databinding.ItemDiscussionBinding;
import com.example.animecentralapp.model.Discussion;
import com.example.animecentralapp.util.TimeAgo;

import java.util.Objects;

/** The thread list on the Details page's Discussion tab. */
public class DiscussionAdapter extends ListAdapter<Discussion, DiscussionAdapter.Holder> {

    public interface OnThreadClick { void onThreadClick(Discussion thread); }

    private final OnThreadClick clickListener;

    public DiscussionAdapter(OnThreadClick clickListener) {
        super(DIFF);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Discussion> DIFF =
            new DiffUtil.ItemCallback<Discussion>() {
                @Override
                public boolean areItemsTheSame(@NonNull Discussion a, @NonNull Discussion b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Discussion a, @NonNull Discussion b) {
                    return Objects.equals(a.getTitle(), b.getTitle())
                            && Objects.equals(a.getBody(), b.getBody())
                            && a.isSpoiler() == b.isSpoiler()
                            && a.getReplyCount() == b.getReplyCount()
                            && Objects.equals(a.getUsername(), b.getUsername());
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDiscussionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemDiscussionBinding b;

        Holder(ItemDiscussionBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Discussion d) {
            b.discussionTitle.setText(d.getTitle());

            // A spoiler thread never shows its text in the list
            String body = d.getBody() == null ? "" : d.getBody().trim();
            if (d.isSpoiler()) {
                b.discussionPreview.setText("Contains spoilers. Open to read.");
                b.discussionPreview.setVisibility(View.VISIBLE);
            } else if (body.isEmpty()) {
                b.discussionPreview.setVisibility(View.GONE);
            } else {
                b.discussionPreview.setText(body);
                b.discussionPreview.setVisibility(View.VISIBLE);
            }
            b.discussionSpoilerTag.setVisibility(d.isSpoiler() ? View.VISIBLE : View.GONE);

            String name = d.getUsername() == null || d.getUsername().isEmpty()
                    ? "Guest" : d.getUsername();
            int n = d.getReplyCount();
            String replies = n <= 0 ? "No replies" : n + (n == 1 ? " reply" : " replies");
            b.discussionMeta.setText(name + "  ·  " + TimeAgo.ago(d.getTimestamp())
                    + "  ·  " + replies);

            itemView.setOnClickListener(v -> clickListener.onThreadClick(d));
        }
    }
}