package com.example.animecentralapp.ui.adapters;

import android.animation.AnimatorInflater;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemContinueBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.ShimmerDrawable;

import java.util.ArrayList;
import java.util.List;

/** The "Continue watching" row: wide pictures with a progress bar. */
public class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.ContinueViewHolder> {

    /** One anime the user has started but not finished. */
    public static class Item {
        public final Anime anime;
        @Nullable public final String episodeId;
        public int episodeNumber; // 0 until we know it
        public int total;         // total episodes, 0 until we know it

        public Item(Anime anime, @Nullable String episodeId) {
            this.anime = anime;
            this.episodeId = episodeId;
        }
    }

    public interface Listener {
        void onClick(Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;

    public ContinueAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContinueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ContinueViewHolder(ItemContinueBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ContinueViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ContinueViewHolder extends RecyclerView.ViewHolder {
        private final ItemContinueBinding binding;

        ContinueViewHolder(ItemContinueBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                    itemView.getContext(), R.animator.ac_press));
        }

        void bind(Item item) {
            Anime anime = item.anime;
            binding.continueTitle.setText(anime.getTitle());

            if (item.episodeNumber > 0) {
                binding.continueSub.setText(item.total > 0
                        ? "Episode " + item.episodeNumber + " of " + item.total
                        : "Episode " + item.episodeNumber);
                binding.continueProgress.setProgress(item.total > 0
                        ? Math.min(100, item.episodeNumber * 100 / item.total) : 0);
            } else {
                binding.continueSub.setText("Resume");
                binding.continueProgress.setProgress(0);
            }

            String image = anime.getThumbnailUrl();
            if (image == null || image.isEmpty()) image = anime.getPosterUrl();
            Glide.with(itemView.getContext())
                    .load(image)
                    .placeholder(new ShimmerDrawable(itemView.getContext(), 0))
                    .into(binding.continueImage);

            itemView.setOnClickListener(v -> listener.onClick(item));
        }
    }
}
