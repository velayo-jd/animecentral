package com.example.animecentralapp.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.animecentralapp.model.Episode;
import com.example.animecentralapp.util.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the episodes subcollection: anime/{animeId}/episodes
 */
public class EpisodeRepository {
    private static final String TAG = "EpisodeRepository";
    private ListenerRegistration registration;

    public LiveData<List<Episode>> getEpisodes(String animeId) {
        MutableLiveData<List<Episode>> liveData = new MutableLiveData<>();

        registration = FirebaseUtil.getFirestore()
                .collection("anime")
                .document(animeId)
                .collection("episodes")
                .orderBy("number")
                .addSnapshotListener((snapshot, error) -> {
                    List<Episode> list = new ArrayList<>();
                    if (error != null) {
                        Log.e(TAG, "Failed to load episodes", error);
                        liveData.setValue(list);
                        return;
                    }
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Episode episode = doc.toObject(Episode.class);
                            if (episode != null) {
                                episode.setId(doc.getId());
                                episode.setAnimeId(animeId);
                                list.add(episode);
                            }
                        }
                    }
                    liveData.setValue(list);
                });

        return liveData;
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
