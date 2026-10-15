package com.example.animecentralapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.repository.AnimeRepository;

import androidx.lifecycle.MediatorLiveData;

import java.util.List;

public class AnimeViewModel extends ViewModel {
    private final AnimeRepository repository = new AnimeRepository();
    private final MediatorLiveData<List<Anime>> all = new MediatorLiveData<>();
    private LiveData<List<Anime>> source;

    public LiveData<List<Anime>> getAllAnime() {
        if (source == null) refreshAllAnime();
        return all;
    }

    /** Re-reads the catalogue from Firestore; observers get the new list. */
    public void refreshAllAnime() {
        if (source != null) all.removeSource(source);
        source = repository.getAllAnime();
        all.addSource(source, all::setValue);
    }

    public LiveData<Anime> getAnime(String animeId) {
        return repository.getAnimeById(animeId);
    }
    public LiveData<Anime> getAnimeById(String animeId) {
        return repository.getAnimeById(animeId);
    }
}
