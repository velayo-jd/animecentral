package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemAnimeBinding;
import com.example.animecentralapp.model.Anime;

import java.util.Objects;

public class AnimeAdapter extends ListAdapter<Anime, AnimeAdapter.AnimeViewHolder> {

    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }

    private final OnAnimeClickListener clickListener;
    private final int cardHeightDp; // 0 = use the height from item_anime.xml
    private final int cardWidthDp;  // 0 = match the parent's width (grid)

    public AnimeAdapter(OnAnimeClickListener clickListener) {
        this(clickListener, 0);
    }

    /** Use a custom card height, e.g. taller cards on the Videos tab. */
    public AnimeAdapter(OnAnimeClickListener clickListener, int cardHeightDp) {
        this(clickListener, cardHeightDp, 0);
    }

    /** Fixed-size cards, used by the sideways-scrolling rows on Home. */
    public AnimeAdapter(OnAnimeClickListener clickListener, int cardHeightDp, int cardWidthDp) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.cardHeightDp = cardHeightDp;
        this.cardWidthDp = cardWidthDp;
    }

    private static final DiffUtil.ItemCallback<Anime> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Anime>() {
                @Override
                public boolean areItemsTheSame(@NonNull Anime oldItem, @NonNull Anime newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Anime oldItem, @NonNull Anime newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getPosterUrl(), newItem.getPosterUrl());
                }
            };

    @NonNull
    @Override
    public AnimeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAnimeBinding binding = ItemAnimeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AnimeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AnimeViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class AnimeViewHolder extends RecyclerView.ViewHolder {
        private final ItemAnimeBinding binding;

        public AnimeViewHolder(ItemAnimeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Anime anime) {
            binding.animeTitle.setText(anime.getTitle());

            float density = itemView.getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams lp = binding.getRoot().getLayoutParams();
            if (cardHeightDp > 0) lp.height = (int) (cardHeightDp * density);
            if (cardWidthDp > 0) lp.width = (int) (cardWidthDp * density);
            binding.getRoot().setLayoutParams(lp);

            Glide.with(itemView.getContext())
                    .load(anime.getPosterUrl())
                    .placeholder(R.drawable.placeholder)
                    .fallback(R.drawable.placeholder)
                    .into(binding.animeImage);

            itemView.setOnClickListener(v -> clickListener.onAnimeClick(anime));
        }
    }
}
