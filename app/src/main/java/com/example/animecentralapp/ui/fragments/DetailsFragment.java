package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentDetailsBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.model.Episode;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.EpisodeAdapter;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.example.animecentralapp.viewmodel.EpisodeViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Anime details screen (shown before watching): poster, info chips, synopsis and the episode list.
 */
public class DetailsFragment extends Fragment {
    private FragmentDetailsBinding binding;
    private String animeId;
    private List<Episode> episodes = new ArrayList<>();
    private EpisodeAdapter episodeAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        animeId = args != null ? args.getString("animeId") : null;
        if (animeId == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        updateHeart();
        binding.favoriteBtn.setOnClickListener(v -> {
            boolean nowFavorite = LibraryStore.toggleFavorite(requireContext(), animeId);
            updateHeart();
            Toast.makeText(requireContext(),
                    nowFavorite ? "Added to Favorites" : "Removed from Favorites",
                    Toast.LENGTH_SHORT).show();
        });

        AnimeViewModel animeViewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        EpisodeViewModel episodeViewModel = new ViewModelProvider(this).get(EpisodeViewModel.class);

        episodeAdapter = new EpisodeAdapter((episode, position) -> openWatch(episode.getId()));
        binding.episodesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.episodesRecyclerView.setAdapter(episodeAdapter);
        binding.episodesRecyclerView.setNestedScrollingEnabled(false);

        animeViewModel.getAnimeById(animeId).observe(getViewLifecycleOwner(), this::showAnime);

        episodeViewModel.getEpisodes(animeId).observe(getViewLifecycleOwner(), list -> {
            episodes = list == null ? new ArrayList<>() : list;
            episodeAdapter.submitList(new ArrayList<>(episodes));
            binding.noEpisodesText.setVisibility(episodes.isEmpty() ? View.VISIBLE : View.GONE);
        });

        binding.watchNowBtn.setOnClickListener(v -> {
            if (episodes.isEmpty()) {
                Toast.makeText(requireContext(), "No episodes yet", Toast.LENGTH_SHORT).show();
            } else {
                openWatch(episodes.get(0).getId());
            }
        });
    }

    private void updateHeart() {
        boolean fav = LibraryStore.isFavorite(requireContext(), animeId);
        binding.favoriteBtn.setImageResource(
                fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void showAnime(@Nullable Anime anime) {
        if (anime == null || binding == null) return;

        binding.animeTitle.setText(anime.getTitle());
        String synopsis = anime.getSynopsis();
        binding.animeSynopsis.setText(synopsis == null || synopsis.trim().isEmpty()
                ? "No synopsis yet." : synopsis);

        setChip(binding.chipStatus, anime.getStatus());
        setChip(binding.chipEpisodes, anime.getEpisodes() > 0 ? anime.getEpisodes() + " eps" : null);
        setChip(binding.chipYear, anime.getYear() > 0 ? String.valueOf(anime.getYear()) : null);

        List<String> genres = anime.getGenres();
        if (genres != null && !genres.isEmpty()) {
            binding.animeGenres.setVisibility(View.VISIBLE);
            binding.animeGenres.setText("Genres: " + TextUtils.join(", ", genres));
        } else {
            binding.animeGenres.setVisibility(View.GONE);
        }

        Glide.with(this).load(anime.getPosterUrl()).into(binding.animePoster);
    }

    private void setChip(android.widget.TextView chip, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            chip.setVisibility(View.GONE);
        } else {
            chip.setVisibility(View.VISIBLE);
            chip.setText(text);
        }
    }

    private void openWatch(String episodeId) {
        Bundle args = new Bundle();
        args.putString("animeId", animeId);
        args.putString("episodeId", episodeId);
        NavHostFragment.findNavController(this).navigate(R.id.watch, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
