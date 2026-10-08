package com.example.animecentralapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.repository.AnimeRepository;

import java.util.List;

public class AnimeViewModel extends ViewModel {
    private final AnimeRepository repository = new AnimeRepository();
    private final LiveData<List<Anime>> allAnime = repository.getAllAnime();

    public LiveData<List<Anime>> getAllAnime() {
        return allAnime;
    }

    public LiveData<Anime> getAnimeById(String animeId) {
        return repository.getAnimeById(animeId);
    }

    public LiveData<List<Anime>> searchAnime(String query) {
        return repository.searchAnime(query);
    }
}
