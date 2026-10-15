package com.example.animecentralapp.ui.adapters;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemEpisodeBinding;
import com.example.animecentralapp.model.Episode;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Episode rows: unwatched dot, "Episode N", title, bookmark mark and a play button.
 * Watched/bookmarked state comes from the fragment (see EpisodeStore).
 */
public class EpisodeAdapter extends ListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder> {

    public interface OnEpisodeClickListener {
        void onEpisodeClick(Episode episode, int position);
    }

    private final OnEpisodeClickListener clickListener;
    private OnEpisodeClickListener longClickListener; // admins only (edit / delete)
    private String currentEpisodeId;
    private Set<String> watched = new HashSet<>();
    private Set<String> bookmarked = new HashSet<>();

    public EpisodeAdapter(OnEpisodeClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Episode> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Episode>() {
                @Override
                public boolean areItemsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
                    return oldItem.getNumber() == newItem.getNumber()
                            && Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getVimeoId(), newItem.getVimeoId());
                }
            };

    public void setLongClickListener(OnEpisodeClickListener l) { longClickListener = l; }

    /** Highlights the episode the viewer is up to (the last one they opened), or null for none. */
    public void setCurrentEpisodeId(@Nullable String episodeId) {
        if (Objects.equals(currentEpisodeId, episodeId)) return;
        currentEpisodeId = episodeId;
        notifyDataSetChanged();
    }

    public void setStates(Set<String> watched, Set<String> bookmarked) {
        this.watched = watched;
        this.bookmarked = bookmarked;
        notifyDataSetChanged();
    }

    public boolean isWatched(Episode e) { return e.getId() != null && watched.contains(e.getId()); }

    public boolean isBookmarked(Episode e) { return e.getId() != null && bookmarked.contains(e.getId()); }

    /** Public so the swipe callback can read the row's episode. */
    public Episode episodeAt(int position) { return getItem(position); }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEpisodeBinding binding = ItemEpisodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private final ItemEpisodeBinding binding;

        public EpisodeViewHolder(ItemEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Episode episode) {
            boolean isWatched = isWatched(episode);
            boolean current = episode.getId() != null && episode.getId().equals(currentEpisodeId);

            binding.episodeNumber.setText("Episode " + episode.getNumber());

            // Subtitle: the episode title, plus an accent "Continue watching" on the current one
            SpannableStringBuilder sub = new SpannableStringBuilder();
            String title = episode.getTitle();
            if (title != null && !title.isEmpty()) sub.append(title);
            // Only on rows that aren't greyed out (a watched row is already dimmed)
            if (current && !isWatched) {
                if (sub.length() > 0) sub.append("  ·  ");
                int start = sub.length();
                sub.append("Continue watching");
                sub.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(itemView.getContext(), R.color.ac_accent)),
                        start, sub.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.episodeTitle.setText(sub);
            binding.episodeTitle.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

            // Watched rows lose the dot and fade a little
            binding.episodeDot.setVisibility(isWatched ? View.INVISIBLE : View.VISIBLE);
            binding.episodeText.setAlpha(isWatched ? 0.5f : 1f);
            binding.episodeBookmark.setVisibility(isBookmarked(episode) ? View.VISIBLE : View.GONE);

            View.OnClickListener open = v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onEpisodeClick(getItem(pos), pos);
                }
            };
            itemView.setOnClickListener(open);
            binding.episodePlay.setOnClickListener(open);
            if (longClickListener != null) {
                itemView.setOnLongClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;
                    longClickListener.onEpisodeClick(getItem(pos), pos);
                    return true;
                });
            } else {
                itemView.setOnLongClickListener(null);
                itemView.setLongClickable(false);
            }
        }
    }
}
