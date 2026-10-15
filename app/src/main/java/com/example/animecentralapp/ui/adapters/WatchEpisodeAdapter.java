package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemWatchEpisodeBinding;
import com.example.animecentralapp.model.Episode;

import java.util.Objects;

/** Episode number tiles under the player: current = accent, finished = grayed out. */
public class WatchEpisodeAdapter
        extends ListAdapter<Episode, WatchEpisodeAdapter.Holder> {

    public interface OnPick { void onPick(Episode episode, int position); }

    private final OnPick onPick;
    private String currentId;
    private java.util.Set<String> watched = new java.util.HashSet<>();
    public interface OnToggleSaved { void onToggle(Episode episode); }

    private OnToggleSaved onToggleSaved;
    private java.util.Set<String> bookmarked = new java.util.HashSet<>();

    public void setOnToggleSaved(OnToggleSaved l) { onToggleSaved = l; }

    /** Episodes the user saved (bookmarked). Shown with a small bookmark on the tile. */
    public void setBookmarked(java.util.Set<String> ids) {
        bookmarked = ids == null ? new java.util.HashSet<>() : new java.util.HashSet<>(ids);
        notifyDataSetChanged();
    }

    /** Episodes already finished (shown grayed out). */
    public void setWatched(java.util.Set<String> ids) {
        watched = ids == null ? new java.util.HashSet<>() : new java.util.HashSet<>(ids);
        notifyDataSetChanged();
    }

    public WatchEpisodeAdapter(OnPick onPick) {
        super(new DiffUtil.ItemCallback<Episode>() {
            @Override public boolean areItemsTheSame(@NonNull Episode a, @NonNull Episode b) {
                return Objects.equals(a.getId(), b.getId());
            }
            @Override public boolean areContentsTheSame(@NonNull Episode a, @NonNull Episode b) {
                return a.getNumber() == b.getNumber()
                        && Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(a.getThumbnailUrl(), b.getThumbnailUrl());
            }
        });
        this.onPick = onPick;
    }

    /** The episode that is playing right now. */
    public void setCurrentEpisodeId(@Nullable String id) {
        if (Objects.equals(currentId, id)) return;
        currentId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWatchEpisodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemWatchEpisodeBinding b;

        Holder(ItemWatchEpisodeBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Episode e) {
            android.content.Context c = itemView.getContext();
            boolean current = e.getId() != null && e.getId().equals(currentId);
            boolean done = !current && e.getId() != null && watched.contains(e.getId());

            b.watchEpNumber.setText(String.valueOf(e.getNumber()));
            b.watchEpNumber.setBackgroundResource(current ? R.drawable.ac_ep_tile_on
                    : done ? R.drawable.ac_ep_tile_done : R.drawable.ac_ep_tile);
            b.watchEpNumber.setTextColor(ContextCompat.getColor(c,
                    current ? R.color.ac_on_accent : done ? R.color.ac_text_faint : R.color.ac_text));
            b.watchEpNumber.setContentDescription("Episode " + e.getNumber()
                    + (current ? ", now playing" : done ? ", watched" : ""));

            boolean saved = e.getId() != null && bookmarked.contains(e.getId());
            b.watchEpBookmark.setVisibility(saved ? android.view.View.VISIBLE : android.view.View.GONE);
// on the accent-coloured "now playing" tile the icon must not be accent too
            b.watchEpBookmark.setColorFilter(ContextCompat.getColor(c,
                    current ? R.color.ac_on_accent : R.color.ac_accent));
            b.watchEpNumber.setContentDescription(b.watchEpNumber.getContentDescription()
                    + (saved ? ", saved" : ""));

            b.watchEpNumber.setOnLongClickListener(v -> {
                if (onToggleSaved == null) return false;
                onToggleSaved.onToggle(e);
                return true;
            });

            b.watchEpNumber.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) onPick.onPick(getItem(pos), pos);
            });
        }
    }
}
