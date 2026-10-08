package com.example.animecentralapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.animecentralapp.model.Comment;
import com.example.animecentralapp.repository.CommentRepository;
import com.example.animecentralapp.util.FirebaseUtil;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CommentViewModel extends ViewModel {
    private final CommentRepository repository = new CommentRepository();
    private final MutableLiveData<List<Comment>> comments = new MutableLiveData<>();
    private ListenerRegistration registration;
    private String currentKey;

    public LiveData<List<Comment>> getComments() {
        return comments;
    }

    /** Starts listening to the comments of one episode (switches when the episode changes). */
    public void loadComments(String animeId, String episodeId) {
        if (animeId == null || episodeId == null) return;

        String key = animeId + "/" + episodeId;
        if (key.equals(currentKey)) return;
        currentKey = key;

        if (registration != null) {
            registration.remove();
        }
        comments.setValue(new ArrayList<>());
        registration = repository.listen(animeId, episodeId, comments::setValue);
    }

    public void postComment(String animeId, String episodeId, String text) {
        FirebaseUser user = FirebaseUtil.getAuth().getCurrentUser();

        String uid = "guest";
        String username = "Guest";
        String photoUrl = null;

        if (user != null) {
            uid = user.getUid();
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                username = user.getDisplayName();
            } else if (user.getEmail() != null && user.getEmail().contains("@")) {
                username = user.getEmail().split("@")[0];
            }
            if (user.getPhotoUrl() != null) {
                photoUrl = user.getPhotoUrl().toString();
            }
        }

        Comment comment = new Comment(animeId, uid, username, text);
        comment.setUserPhotoUrl(photoUrl);
        repository.add(animeId, episodeId, comment);
    }

    @Override
    protected void onCleared() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
