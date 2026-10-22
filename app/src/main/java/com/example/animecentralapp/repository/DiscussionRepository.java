package com.example.animecentralapp.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.animecentralapp.model.Discussion;
import com.example.animecentralapp.model.DiscussionReply;
import com.example.animecentralapp.util.FirebaseUtil;
import com.example.animecentralapp.util.LibraryStore;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/** Threads and replies for one anime's discussion tab. */
public class DiscussionRepository {
    private static final String TAG = "DiscussionRepository";

    public interface ListCallback<T> { void onResult(List<T> list); }
    public interface ThreadCallback { void onResult(@Nullable Discussion thread); } // null = gone
    public interface Done { void onDone(boolean ok, @Nullable String error); }

    private CollectionReference threads(String animeId) {
        return FirebaseUtil.getFirestore().collection("anime").document(animeId)
                .collection("discussions");
    }

    // ---------- builders (fill in who is posting) ----------

    @Nullable
    public static Discussion newThread(Context ctx, String animeId, String title,
                                       String body, boolean spoiler) {
        FirebaseUser u = FirebaseUtil.getAuth().getCurrentUser();
        if (u == null) return null;
        Discussion d = new Discussion();
        d.setAnimeId(animeId);
        d.setTitle(title);
        d.setBody(body);
        d.setSpoiler(spoiler);
        d.setUserId(u.getUid());
        d.setUsername(LibraryStore.displayName(ctx, u.getDisplayName()));
        d.setUserPhotoUrl(u.getPhotoUrl() != null ? u.getPhotoUrl().toString() : null);
        d.setTimestamp(System.currentTimeMillis());
        d.setReplyCount(0);
        return d;
    }

    @Nullable
    public static DiscussionReply newReply(Context ctx, String text, boolean spoiler) {
        FirebaseUser u = FirebaseUtil.getAuth().getCurrentUser();
        if (u == null) return null;
        DiscussionReply r = new DiscussionReply();
        r.setText(text);
        r.setSpoiler(spoiler);
        r.setUserId(u.getUid());
        r.setUsername(LibraryStore.displayName(ctx, u.getDisplayName()));
        r.setUserPhotoUrl(u.getPhotoUrl() != null ? u.getPhotoUrl().toString() : null);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // ---------- listening ----------

    public ListenerRegistration listenThreads(String animeId, ListCallback<Discussion> cb) {
        return threads(animeId).orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    List<Discussion> list = new ArrayList<>();
                    if (error != null) {
                        Log.e(TAG, "Failed to load threads", error);
                        cb.onResult(list);
                        return;
                    }
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Discussion d = null;
                            try {
                                d = doc.toObject(Discussion.class);
                            } catch (RuntimeException e) {
                                Log.w(TAG, "Skipping bad thread " + doc.getId() + ": " + e.getMessage());
                            }
                            if (d != null) {
                                d.setId(doc.getId());
                                list.add(d);
                            }
                        }
                    }
                    cb.onResult(list);
                });
    }

    public ListenerRegistration listenThread(String animeId, String threadId, ThreadCallback cb) {
        return threads(animeId).document(threadId).addSnapshotListener((doc, error) -> {
            if (error != null) {
                Log.e(TAG, "Failed to load thread", error);
                return;
            }
            if (doc == null || !doc.exists()) {
                cb.onResult(null);
                return;
            }
            Discussion d = null;
            try {
                d = doc.toObject(Discussion.class);
            } catch (RuntimeException e) {
                Log.w(TAG, "Bad thread " + doc.getId() + ": " + e.getMessage());
            }
            if (d != null) d.setId(doc.getId());
            cb.onResult(d);
        });
    }

    public ListenerRegistration listenReplies(String animeId, String threadId,
                                              ListCallback<DiscussionReply> cb) {
        return threads(animeId).document(threadId).collection("replies")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    List<DiscussionReply> list = new ArrayList<>();
                    if (error != null) {
                        Log.e(TAG, "Failed to load replies", error);
                        cb.onResult(list);
                        return;
                    }
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            DiscussionReply r = null;
                            try {
                                r = doc.toObject(DiscussionReply.class);
                            } catch (RuntimeException e) {
                                Log.w(TAG, "Skipping bad reply " + doc.getId() + ": " + e.getMessage());
                            }
                            if (r != null) {
                                r.setId(doc.getId());
                                list.add(r);
                            }
                        }
                    }
                    cb.onResult(list);
                });
    }

    // ---------- writing ----------

    public void addThread(Discussion d, Done done) {
        threads(d.getAnimeId()).add(d)
                .addOnSuccessListener(ref -> done.onDone(true, null))
                .addOnFailureListener(e -> done.onDone(false, e.getMessage()));
    }

    /** Adds the reply and bumps the thread's reply counter in one go. */
    public void addReply(String animeId, String threadId, DiscussionReply reply, Done done) {
        DocumentReference threadRef = threads(animeId).document(threadId);
        DocumentReference replyRef = threadRef.collection("replies").document();
        WriteBatch batch = FirebaseUtil.getFirestore().batch();
        batch.set(replyRef, reply);
        batch.update(threadRef, "replyCount", FieldValue.increment(1));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(true, null))
                .addOnFailureListener(e -> done.onDone(false, e.getMessage()));
    }

    public void deleteReply(String animeId, String threadId, String replyId, Done done) {
        DocumentReference threadRef = threads(animeId).document(threadId);
        WriteBatch batch = FirebaseUtil.getFirestore().batch();
        batch.delete(threadRef.collection("replies").document(replyId));
        batch.update(threadRef, "replyCount", FieldValue.increment(-1));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(true, null))
                .addOnFailureListener(e -> done.onDone(false, e.getMessage()));
    }

    /** Deletes the thread and all its replies (a batch holds up to 500, plenty for this app). */
    public void deleteThread(String animeId, String threadId, Done done) {
        DocumentReference threadRef = threads(animeId).document(threadId);
        threadRef.collection("replies").get().addOnSuccessListener(snap -> {
            WriteBatch batch = FirebaseUtil.getFirestore().batch();
            for (DocumentSnapshot r : snap.getDocuments()) batch.delete(r.getReference());
            batch.delete(threadRef);
            batch.commit()
                    .addOnSuccessListener(v -> done.onDone(true, null))
                    .addOnFailureListener(e -> done.onDone(false, e.getMessage()));
        }).addOnFailureListener(e -> done.onDone(false, e.getMessage()));
    }
}