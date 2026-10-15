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
 * Videos tab (browse): "Feeling indecisive?" picks, Recently added, Genres, Format and the
 * A-Z list of every anime. Personal lists (continue watching, favorites, saved episodes)
 * live on the History tab.
 */
public class VideosFragment extends Fragment {
    private static final int RECOMMENDED_COUNT = 4;

    private FragmentVideosBinding binding;
    private AnimeAdapter recommendedAdapter;
    private AnimeAdapter recentAdapter;
    private TagAdapter genreAdapter;
    private TagAdapter typeAdapter;
    private List<Anime> allAnime = new ArrayList<>();
    private List<Anime> picks; // what the "Feeling indecisive?" section currently shows

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVideosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int pad = (int) (10 * getResources().getDisplayMetrics().density);

        // Feeling indecisive? (sideways row)
        recommendedAdapter = new AnimeAdapter(this::openDetails, 150, 105);
        binding.recommendedRecycler.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recommendedRecycler.setAdapter(recommendedAdapter);
        binding.recommendedRecycler.setPadding(pad, 0, pad, 0);

        // Recently added (sideways row)
        binding.rowRecent.rowTitle.setText("Recently added");
        recentAdapter = new AnimeAdapter(this::openDetails, 150, 105);
        setupSidewaysRow(binding.rowRecent.rowRecycler, recentAdapter);
        binding.rowRecent.rowRecycler.setPadding(pad, 0, pad, 0);

        // Genres (two rows scrolling sideways together) and Format
        genreAdapter = new TagAdapter(this::openTag, true);
        setupSidewaysRow(binding.genresRecycler, genreAdapter);
        binding.genresRecycler.setLayoutManager(
                new GridLayoutManager(getContext(), 2, GridLayoutManager.HORIZONTAL, false));

        typeAdapter = new TagAdapter(this::openTag, true);
        setupSidewaysRow(binding.typesRecycler, typeAdapter);

        binding.randomizeBtn.setOnClickListener(v -> randomize());

        binding.genresHeader.setOnClickListener(v -> openAllTags("genre"));
        binding.typesHeader.setOnClickListener(v -> openAllTags("type"));

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        binding.rowAll.getRoot().setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.catalog));

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            List<Anime> all = list == null ? new ArrayList<>() : list;
            allAnime = all;

            // First load shows the recommended picks; after that, keep whatever is showing
            if (picks == null) picks = TagUtils.recommended(all, RECOMMENDED_COUNT);
            recommendedAdapter.submitList(new ArrayList<>(picks));

            // Recently added: newest first, only anime that have an addedAt stamp
            List<Anime> recent = new ArrayList<>();
            for (Anime a : all) if (a.getAddedAt() > 0) recent.add(a);
            Collections.sort(recent, (x, y) -> Long.compare(y.getAddedAt(), x.getAddedAt()));
            recentAdapter.submitList(recent.size() > 10
                    ? new ArrayList<>(recent.subList(0, 10)) : recent);
            binding.rowRecent.getRoot().setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

            List<String> genres = TagUtils.collectGenres(all);
            genreAdapter.submitList(genres);
            binding.genresRecycler.setVisibility(genres.isEmpty() ? View.GONE : View.VISIBLE);
            binding.genresEmpty.setVisibility(genres.isEmpty() ? View.VISIBLE : View.GONE);

            // Format chips with how many anime each has, e.g. "TV · 12"
            List<String> types = new ArrayList<>();
            for (String f : TagUtils.collectFormats(all)) {
                int n = 0;
                for (Anime a : all) if (f.equalsIgnoreCase(TagUtils.formatOf(a))) n++;
                types.add(f + " · " + n);
            }
            typeAdapter.submitList(types);
            binding.typesRecycler.setVisibility(types.isEmpty() ? View.GONE : View.VISIBLE);
            binding.typesEmpty.setVisibility(types.isEmpty() ? View.VISIBLE : View.GONE);

            binding.rowAll.catalogCount.setText(all.size() + " anime");
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
        recommendedAdapter.submitList(new ArrayList<>(picks),
                () -> binding.recommendedRecycler.scrollToPosition(0));
    }

    private void setupSidewaysRow(RecyclerView row, RecyclerView.Adapter<?> adapter) {
        row.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(adapter);
    }

    private void openDetails(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    private void openTag(String tag) {
        int cut = tag.indexOf(" · ");
        if (cut > 0) tag = tag.substring(0, cut);   // "TV · 12" -> "TV"
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
