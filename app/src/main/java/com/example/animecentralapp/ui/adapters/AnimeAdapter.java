package com.example.animecentralapp.ui.adapters;

import android.animation.AnimatorInflater;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemAnimeBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.ShimmerDrawable;

import java.util.Objects;

public class AnimeAdapter extends ListAdapter<Anime, AnimeAdapter.AnimeViewHolder> {

    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }

    /** Like the normal click listener, but also gives you the poster picture (for the glide-into-Details motion). */
    public interface OnAnimeViewClickListener {
        void onAnimeClick(Anime anime, View posterView);
    }

    private final OnAnimeClickListener clickListener;
    private OnAnimeViewClickListener viewClickListener; // optional
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

    /** When set, it is used instead of the normal click listener and receives the poster view. */
    public void setViewClickListener(@Nullable OnAnimeViewClickListener listener) {
        this.viewClickListener = listener;
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
            // Slight "press in" when the card is touched
            itemView.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                    itemView.getContext(), R.animator.ac_press));
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
                    .placeholder(new ShimmerDrawable(itemView.getContext(), 0))
                    .fallback(R.drawable.placeholder)
                    .into(binding.animeImage);

            itemView.setOnClickListener(v -> {
                if (viewClickListener != null) {
                    viewClickListener.onAnimeClick(anime, binding.animeImage);
                } else {
                    clickListener.onAnimeClick(anime);
                }
            });
        }
    }
}
