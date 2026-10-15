package com.example.animecentralapp.repository;

import android.util.Log;

import com.example.animecentralapp.model.Comment;
import com.example.animecentralapp.util.FirebaseUtil;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

import com.google.firebase.firestore.WriteBatch;

/**
 * Comments live under each episode: anime/{animeId}/episodes/{episodeId}/comments
 */
public class CommentRepository {
    private static final String TAG = "CommentRepository";

    public interface Callback {
        void onResult(List<Comment> comments);
    }

    private CollectionReference commentsRef(String animeId, String episodeId) {
        return FirebaseUtil.getFirestore()
                .collection("anime")
                .document(animeId)
                .collection("episodes")
                .document(episodeId)
                .collection("comments");
    }

    public ListenerRegistration listen(String animeId, String episodeId, Callback callback) {
        return commentsRef(animeId, episodeId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    List<Comment> list = new ArrayList<>();
                    if (error != null) {
                        Log.e(TAG, "Failed to load comments", error);
                        callback.onResult(list);
                        return;
                    }
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Comment comment = doc.toObject(Comment.class);
                            if (comment != null) {
                                comment.setCommentId(doc.getId());
                                list.add(comment);
                            }
                        }
                    }
                    callback.onResult(list);
                });
    }

    public void add(String animeId, String episodeId, Comment comment) {
        commentsRef(animeId, episodeId)
                .add(comment)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to post comment", e));
    }

    /** Admin: hide a comment from everyone else (or bring it back). */
    public void setHidden(String animeId, String episodeId, String commentId, boolean hidden) {
        commentsRef(animeId, episodeId).document(commentId)
                .update("hidden", hidden)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to hide comment", e));
    }

    /** Admin: delete a comment and every reply under it. */
    public void deleteThread(String animeId, String episodeId, String commentId) {
        CollectionReference ref = commentsRef(animeId, episodeId);
        ref.whereEqualTo("parentId", commentId).get().addOnSuccessListener(snap -> {
            WriteBatch batch = FirebaseUtil.getFirestore().batch();
            for (DocumentSnapshot d : snap.getDocuments()) batch.delete(d.getReference());
            batch.delete(ref.document(commentId));
            batch.commit().addOnFailureListener(e -> Log.e(TAG, "Failed to delete comment", e));
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to find replies", e));
    }
}
