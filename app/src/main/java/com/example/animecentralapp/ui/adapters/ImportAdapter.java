package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ItemImportResultBinding;
import com.example.animecentralapp.util.AniListClient.Media;
import com.example.animecentralapp.util.ShimmerDrawable;

import java.util.HashSet;
import java.util.Set;

/** Search results of the AniList import screen. */
public class ImportAdapter extends ListAdapter<Media, ImportAdapter.Holder> {

    public interface OnPick { void onPick(Media media); }

    private final OnPick onPick;
    private Set<Integer> existing = new HashSet<>();

    public ImportAdapter(OnPick onPick) {
        super(new DiffUtil.ItemCallback<Media>() {
            @Override public boolean areItemsTheSame(@NonNull Media a, @NonNull Media b) {
                return a.id == b.id;
            }
            @Override public boolean areContentsTheSame(@NonNull Media a, @NonNull Media b) {
                return a.id == b.id;
            }
        });
        this.onPick = onPick;
    }

    /** AniList ids that are already in the library (their button says "Update"). */
    public void setExisting(Set<Integer> ids) {
        existing = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemImportResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemImportResultBinding b;

        Holder(ItemImportResultBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Media m) {
            float dp = itemView.getResources().getDisplayMetrics().density;
            b.resultTitle.setText(m.title);
            b.resultMeta.setText(m.summaryLine());
            b.resultGenres.setText(android.text.TextUtils.join(", ", m.genres));
            b.resultGenres.setVisibility(m.genres.isEmpty() ? View.GONE : View.VISIBLE);

            Glide.with(itemView.getContext()).load(m.posterUrl)
                    .placeholder(new ShimmerDrawable(itemView.getContext(), 10 * dp))
                    .into(b.resultPoster);

            boolean has = existing.contains(m.id);
            b.resultAction.setText(has ? "Update" : "Add");
            b.resultAction.setBackgroundResource(has
                    ? R.drawable.ac_genre_chip : R.drawable.ac_details_btn);
            b.resultAction.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    has ? R.color.ac_text : R.color.ac_on_accent));

            View.OnClickListener click = v -> onPick.onPick(m);
            itemView.setOnClickListener(click);
            b.resultAction.setOnClickListener(click);
        }
    }
}
