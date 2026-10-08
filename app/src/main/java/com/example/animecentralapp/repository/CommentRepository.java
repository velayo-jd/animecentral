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
}
