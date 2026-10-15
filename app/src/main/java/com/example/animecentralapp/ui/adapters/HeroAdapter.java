package com.example.animecentralapp.ui.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.databinding.ItemHeroBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.ShimmerDrawable;

import java.util.ArrayList;
import java.util.List;

/** The swipeable featured banner at the top of Home. One page per anime. */
public class HeroAdapter extends RecyclerView.Adapter<HeroAdapter.HeroViewHolder> {

    public interface Listener {
        void onWatch(Anime anime);
        void onDetails(Anime anime);
    }

    private final List<Anime> items = new ArrayList<>();
    private final Listener listener;

    public HeroAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Anime> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HeroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HeroViewHolder(ItemHeroBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HeroViewHolder holder, int position) {
        holder.bind(items.get(position % items.size()));
    }

    @Override
    public int getItemCount() {
        // 1 banner: nothing to loop. 2+: a huge virtual list so you can swipe either way forever.
        return items.size() < 2 ? items.size() : Integer.MAX_VALUE;
    }

    public int realCount() {
        return items.size();
    }

    class HeroViewHolder extends RecyclerView.ViewHolder {
        private final ItemHeroBinding binding;

        HeroViewHolder(ItemHeroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Anime anime) {
            binding.heroTitle.setText(anime.getTitle());

            String status = anime.getStatus();
            if (status == null || status.trim().isEmpty()) {
                binding.heroStatus.setVisibility(View.GONE);
            } else {
                binding.heroStatus.setVisibility(View.VISIBLE);
                binding.heroStatus.setText(status);
            }

            // "2026 • Fantasy • Mystery • 24 eps"
            List<String> parts = new ArrayList<>();
            if (anime.getYear() > 0) parts.add(String.valueOf(anime.getYear()));
            List<String> genres = anime.getGenres();
            if (genres != null) {
                for (int i = 0; i < genres.size() && i < 2; i++) parts.add(genres.get(i));
            }
            if (anime.getEpisodes() > 0) parts.add(anime.getEpisodes() + " eps");
            binding.heroMeta.setText(TextUtils.join("  •  ", parts));
            binding.heroMeta.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);

            String image = anime.getThumbnailUrl();
            if (image == null || image.isEmpty()) image = anime.getPosterUrl();
            Glide.with(itemView.getContext())
                    .load(image)
                    .placeholder(new ShimmerDrawable(itemView.getContext(), 0))
                    .into(binding.heroImage);

            binding.heroWatch.setOnClickListener(v -> listener.onWatch(anime));
            binding.heroDetails.setOnClickListener(v -> listener.onDetails(anime));
            itemView.setOnClickListener(v -> listener.onDetails(anime));
        }
    }
}
