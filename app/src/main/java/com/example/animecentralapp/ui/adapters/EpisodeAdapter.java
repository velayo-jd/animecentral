package com.example.animecentralapp.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.databinding.ItemEpisodeBinding;
import com.example.animecentralapp.model.Episode;

import java.util.Objects;

public class EpisodeAdapter extends ListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder> {

    public interface OnEpisodeClickListener {
        void onEpisodeClick(Episode episode, int position);
    }

    private final OnEpisodeClickListener clickListener;
    private int selectedPosition = -1;

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
                            && Objects.equals(oldItem.getVimeoId(), newItem.getVimeoId())
                            && Objects.equals(oldItem.getThumbnailUrl(), newItem.getThumbnailUrl());
                }
            };

    /** Highlights the episode that is currently playing. */
    public void setSelectedPosition(int position) {
        selectedPosition = position;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEpisodeBinding binding = ItemEpisodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        holder.bind(getItem(position), position == selectedPosition);
    }

    public class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private final ItemEpisodeBinding binding;

        public EpisodeViewHolder(ItemEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Episode episode, boolean selected) {
            String label = "Ep " + episode.getNumber();
            if (episode.getTitle() != null && !episode.getTitle().isEmpty()) {
                label += " · " + episode.getTitle();
            }
            binding.episodeTitle.setText(label);
            binding.episodeTitle.setTextColor(selected ? Color.parseColor("#FF6B6B") : androidx.core.content.ContextCompat.getColor(itemView.getContext(), com.example.animecentralapp.R.color.ac_text));

            // Thumbnails come from the episode's thumbnailUrl field (Vimeo has no free fallback)
            String url = episode.getThumbnailUrl();
            if (url == null || url.trim().isEmpty()) {
                Glide.with(itemView.getContext()).clear(binding.episodeThumbnail);
            } else {
                Glide.with(itemView.getContext()).load(url.trim()).into(binding.episodeThumbnail);
            }

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onEpisodeClick(getItem(pos), pos);
                }
            });
        }
    }
}
