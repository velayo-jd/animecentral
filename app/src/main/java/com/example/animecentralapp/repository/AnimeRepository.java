package com.example.animecentralapp.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Anime data operations with Firestore
 */
public class AnimeRepository {
    private static final String ANIME_COLLECTION = "anime";
    private final MutableLiveData<List<Anime>> animeListLiveData = new MutableLiveData<>();
    private final MutableLiveData<Anime> animeDetailLiveData = new MutableLiveData<>();

    public LiveData<List<Anime>> getAllAnime() {
        FirebaseUtil.getFirestore().collection(ANIME_COLLECTION)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    List<Anime> animeList = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Anime anime = null;
                            try {
                                anime = doc.toObject(Anime.class);
                            } catch (RuntimeException e) {
                                android.util.Log.w("AnimeRepository",
                                        "Skipping bad anime document " + doc.getId() + ": " + e.getMessage());
                            }
                            if (anime != null) {
                                anime.setId(doc.getId());
                                animeList.add(anime);
                            }
                        }
                    }
                    animeListLiveData.setValue(animeList);
                });
        return animeListLiveData;
    }

    public LiveData<Anime> getAnimeById(String animeId) {
        FirebaseUtil.getFirestore().collection(ANIME_COLLECTION)
                .document(animeId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        return;
                    }
                    Anime anime = value.toObject(Anime.class);
                    if (anime != null) {
                        anime.setId(value.getId());
                    }
                    animeDetailLiveData.setValue(anime);
                });
        return animeDetailLiveData;
    }

    public LiveData<List<Anime>> searchAnime(String query) {
        MutableLiveData<List<Anime>> searchResults = new MutableLiveData<>();
        FirebaseUtil.getFirestore().collection(ANIME_COLLECTION)
                .whereEqualTo("status", "Airing")
                .addSnapshotListener((value, error) -> {
                    List<Anime> results = new ArrayList<>();
                    if (error != null || value == null) {
                        searchResults.setValue(results);
                        return;
                    }
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Anime anime = null;
                        try {
                            anime = doc.toObject(Anime.class);
                        } catch (RuntimeException e) {
                            android.util.Log.w("AnimeRepository",
                                    "Skipping bad anime document " + doc.getId() + ": " + e.getMessage());
                        }
                        if (anime != null && anime.getTitle().toLowerCase().contains(query.toLowerCase())) {
                            anime.setId(doc.getId());
                            results.add(anime);
                        }
                    }
                    searchResults.setValue(results);
                });
        return searchResults;
    }

    public void addAnime(Anime anime) {
        FirebaseUtil.getFirestore().collection(ANIME_COLLECTION)
                .add(anime)
                .addOnSuccessListener(documentReference -> {
                    // Successfully added
                })
                .addOnFailureListener(e -> {
                    // Handle error
                });
    }
}
