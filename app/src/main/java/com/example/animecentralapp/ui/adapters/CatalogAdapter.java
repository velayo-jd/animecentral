package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.databinding.ItemCatalogRowBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.TagUtils;

import java.util.Objects;

/** Plain list rows for the Full Anime Catalogue: title on the left, format on the right. */
public class CatalogAdapter extends ListAdapter<Anime, CatalogAdapter.Holder> {

    public interface OnPick { void onPick(Anime anime); }

    private final OnPick onPick;

    public CatalogAdapter(OnPick onPick) {
        super(new DiffUtil.ItemCallback<Anime>() {
            @Override public boolean areItemsTheSame(@NonNull Anime a, @NonNull Anime b) {
                return Objects.equals(a.getId(), b.getId());
            }
            @Override public boolean areContentsTheSame(@NonNull Anime a, @NonNull Anime b) {
                return Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(TagUtils.formatOf(a), TagUtils.formatOf(b));
            }
        });
        this.onPick = onPick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCatalogRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Anime a = getItem(position);
        h.b.catalogTitle.setText(a.getTitle());
        h.b.catalogFormat.setText(TagUtils.formatOf(a));
        h.b.getRoot().setOnClickListener(v -> onPick.onPick(a));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemCatalogRowBinding b;
        Holder(ItemCatalogRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
