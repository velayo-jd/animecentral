package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentVideosBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.ui.adapters.TagAdapter;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Videos tab: recommended anime on top, then two sideways-scrolling rows:
 * Genres and Type. Tap a chip to see its anime, or "See all" to list every genre / type.
 */
public class VideosFragment extends Fragment {
    private static final int RECOMMENDED_COUNT = 4;

    private FragmentVideosBinding binding;
    private AnimeAdapter recommendedAdapter;
    private TagAdapter genreAdapter;
    private TagAdapter typeAdapter;
    private List<Anime> allAnime = new ArrayList<>();
    private List<Anime> picks; // what the "I'm feeling indecisive" section currently shows

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVideosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recommendedAdapter = new AnimeAdapter(this::openDetails, 270);
        binding.recommendedRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recommendedRecycler.setAdapter(recommendedAdapter);
        binding.recommendedRecycler.setNestedScrollingEnabled(false);

        genreAdapter = new TagAdapter(this::openTag, true);
        setupSidewaysRow(binding.genresRecycler, genreAdapter);

        typeAdapter = new TagAdapter(this::openTag, true);
        setupSidewaysRow(binding.typesRecycler, typeAdapter);

        binding.randomizeBtn.setOnClickListener(v -> randomize());

        binding.genresHeader.setOnClickListener(v -> openAllTags("genre"));
        binding.typesHeader.setOnClickListener(v -> openAllTags("type"));

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            List<Anime> all = list == null ? new ArrayList<>() : list;
            allAnime = all;
            // First load shows the recommended picks; after that, keep whatever is showing
            if (picks == null) picks = TagUtils.recommended(all, RECOMMENDED_COUNT);
            recommendedAdapter.submitList(new ArrayList<>(picks));

            List<String> genres = TagUtils.collectGenres(all);
            genreAdapter.submitList(genres);
            binding.genresRecycler.setVisibility(genres.isEmpty() ? View.GONE : View.VISIBLE);
            binding.genresEmpty.setVisibility(genres.isEmpty() ? View.VISIBLE : View.GONE);

            List<String> types = TagUtils.collectTypes(all);
            typeAdapter.submitList(types);
            binding.typesRecycler.setVisibility(types.isEmpty() ? View.GONE : View.VISIBLE);
            binding.typesEmpty.setVisibility(types.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    /** Shows four random anime, trying not to repeat the ones already on screen. */
    private void randomize() {
        if (allAnime.isEmpty()) return;

        List<Anime> pool = new ArrayList<>(allAnime);
        Collections.shuffle(pool);

        // Put anime that aren't currently shown first, so each press feels different
        List<Anime> fresh = new ArrayList<>();
        List<Anime> repeats = new ArrayList<>();
        for (Anime a : pool) {
            boolean shown = false;
            if (picks != null) {
                for (Anime p : picks) {
                    if (p.getId() != null && p.getId().equals(a.getId())) { shown = true; break; }
                }
            }
            (shown ? repeats : fresh).add(a);
        }
        fresh.addAll(repeats);

        picks = new ArrayList<>(fresh.subList(0, Math.min(RECOMMENDED_COUNT, fresh.size())));
        recommendedAdapter.submitList(new ArrayList<>(picks));
    }

    private void setupSidewaysRow(RecyclerView row, TagAdapter adapter) {
        row.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(adapter);
    }

    private void openDetails(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    private void openTag(String tag) {
        Bundle args = new Bundle();
        args.putString("tag", tag);
        NavHostFragment.findNavController(this).navigate(R.id.tag_browse, args);
    }

    private void openAllTags(String kind) {
        Bundle args = new Bundle();
        args.putString("kind", kind);
        NavHostFragment.findNavController(this).navigate(R.id.all_tags, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
