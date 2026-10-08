package com.example.animecentralapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.databinding.ItemCommentBinding;
import com.example.animecentralapp.model.Comment;

import java.util.Objects;

public class CommentAdapter extends ListAdapter<Comment, CommentAdapter.CommentViewHolder> {

    public CommentAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Comment> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Comment>() {
                @Override
                public boolean areItemsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
                    return Objects.equals(oldItem.getCommentId(), newItem.getCommentId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
                    return Objects.equals(oldItem.getText(), newItem.getText())
                            && Objects.equals(oldItem.getUsername(), newItem.getUsername());
                }
            };

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CommentViewHolder(ItemCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ItemCommentBinding binding;

        public CommentViewHolder(ItemCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Comment comment) {
            String name = comment.getUsername();
            binding.commentUsername.setText(name == null || name.isEmpty() ? "Guest" : name);
            binding.commentText.setText(comment.getText());

            String photo = comment.getUserPhotoUrl();
            if (photo != null && !photo.isEmpty()) {
                Glide.with(itemView.getContext()).load(photo).circleCrop()
                        .into(binding.userProfilePicture);
            } else {
                Glide.with(itemView.getContext()).clear(binding.userProfilePicture);
            }
        }
    }
}
