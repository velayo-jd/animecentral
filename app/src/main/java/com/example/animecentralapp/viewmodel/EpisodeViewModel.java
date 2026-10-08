package com.example.animecentralapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.animecentralapp.model.Episode;
import com.example.animecentralapp.repository.EpisodeRepository;

import java.util.List;

public class EpisodeViewModel extends ViewModel {
    private final EpisodeRepository repository = new EpisodeRepository();
    private LiveData<List<Episode>> episodes;

    public LiveData<List<Episode>> getEpisodes(String animeId) {
        if (episodes == null) {
            episodes = repository.getEpisodes(animeId);
        }
        return episodes;
    }

    @Override
    protected void onCleared() {
        repository.stop();
    }
}
