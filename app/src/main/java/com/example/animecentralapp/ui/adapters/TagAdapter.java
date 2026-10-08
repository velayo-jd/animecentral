package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;

/** Shows tags either as grid boxes (item_tag) or as small chips for sideways lists (item_tag_chip). */
public class TagAdapter extends ListAdapter<String, TagAdapter.TagViewHolder> {

    public interface OnTagClickListener {
        void onTagClick(String tag);
    }

    private final OnTagClickListener listener;
    private final int layoutRes;

    /** Grid boxes. */
    public TagAdapter(OnTagClickListener listener) {
        this(listener, false);
    }

    public TagAdapter(OnTagClickListener listener, boolean chips) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String a, @NonNull String b) {
                return a.equals(b);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String a, @NonNull String b) {
                return a.equals(b);
            }
        });
        this.listener = listener;
        this.layoutRes = chips ? R.layout.item_tag_chip : R.layout.item_tag;
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new TagViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        String tag = getItem(position);
        holder.text.setText(tag);
        holder.itemView.setOnClickListener(v -> listener.onTagClick(tag));
    }

    static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TagViewHolder(View itemView) {
            super(itemView);
            this.text = itemView.findViewById(R.id.tag_text);
        }
    }
}
