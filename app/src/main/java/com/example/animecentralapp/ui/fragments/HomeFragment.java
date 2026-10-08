package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.databinding.FragmentHomeBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import com.example.animecentralapp.util.TagUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private AnimeViewModel viewModel;
    private Anime featured;

    /** Number of genre rows shown on Home. */
    private static final int MAX_SECTIONS = 5;
    private static final int MAX_PER_SECTION = 10;

    /**
     * Created once per app process, so the genre rows are reshuffled on every fresh app open
     * but stay the same while switching between tabs.
     */
    private static final long SESSION_SEED = System.nanoTime();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        setupButtons();
        observeData();
    }

    private void setupButtons() {
        binding.detailsBtn.setOnClickListener(v -> {
            if (featured != null) openDetails(featured);
        });
        binding.watchBtn.setOnClickListener(v -> {
            if (featured != null) openWatch(featured);
        });

        // Top bar: hamburger opens the side menu, search icon opens Search
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));
    }

    private void observeData() {
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), animeList -> {
            updateFeatured(animeList);
            buildGenreSections(animeList);
        });
    }

    private void updateFeatured(List<Anime> animeList) {
        if (animeList == null || animeList.isEmpty()) {
            featured = null;
            return;
        }
        featured = animeList.get(0);
        binding.featuredTitle.setText(featured.getTitle());

        String status = featured.getStatus();
        if (status == null || status.trim().isEmpty()) {
            binding.featuredStatus.setVisibility(View.GONE);
        } else {
            binding.featuredStatus.setVisibility(View.VISIBLE);
            binding.featuredStatus.setText(status);
        }
        binding.chipEpisodes.setText(featured.getEpisodes() > 0
                ? "CC  " + featured.getEpisodes() : "CC");

        String image = featured.getThumbnailUrl();
        if (image == null || image.isEmpty()) {
            image = featured.getPosterUrl();
        }
        Glide.with(this).load(image).into(binding.featuredImage);
    }

    private void buildGenreSections(List<Anime> all) {
        binding.genreSections.removeAllViews();
        if (all == null || all.isEmpty()) return;

        // Random but stable for this app session
        List<String> genres = TagUtils.collectGenres(all);
        Collections.shuffle(genres, new Random(SESSION_SEED));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int shown = 0;
        for (String genre : genres) {
            if (shown >= MAX_SECTIONS) break;

            List<Anime> matches = TagUtils.filterByTag(all, genre);
            if (matches.isEmpty()) continue;
            Collections.shuffle(matches, new Random(SESSION_SEED + genre.hashCode()));
            if (matches.size() > MAX_PER_SECTION) {
                matches = new ArrayList<>(matches.subList(0, MAX_PER_SECTION));
            }

            View section = inflater.inflate(R.layout.item_home_section,
                    binding.genreSections, false);
            ((TextView) section.findViewById(R.id.section_title)).setText(genre);
            section.findViewById(R.id.section_see_all).setOnClickListener(v -> openTag(genre));

            AnimeAdapter rowAdapter = new AnimeAdapter(this::openDetails, 190, 130);
            RecyclerView row = section.findViewById(R.id.section_recycler);
            row.setLayoutManager(new LinearLayoutManager(
                    requireContext(), LinearLayoutManager.HORIZONTAL, false));
            row.setAdapter(rowAdapter);
            rowAdapter.submitList(matches);

            binding.genreSections.addView(section);
            shown++;
        }
    }

    private void openTag(String tag) {
        Bundle args = new Bundle();
        args.putString("tag", tag);
        NavHostFragment.findNavController(this).navigate(R.id.tag_browse, args);
    }

    private void openDetails(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    private void openWatch(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.watch, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
