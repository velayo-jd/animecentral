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

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentTagBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen for the side-menu lists. Opened with a Bundle: mode =
 * "favorites", "history", "continue", "airing" or "completed".
 * (Reuses fragment_tag.xml: top bar, back arrow, title, grid.)
 */
public class CollectionFragment extends Fragment {
    private FragmentTagBinding binding;
    private AnimeAdapter adapter;
    private String mode = "history";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTagBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null && args.getString("mode") != null) mode = args.getString("mode");

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));
        binding.backBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.tagTitle.setText(titleFor(mode));
        binding.emptyText.setText(emptyFor(mode));

        adapter = new AnimeAdapter(this::onAnimeClick);
        binding.tagResultsRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.tagResultsRecycler.setAdapter(adapter);

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            List<Anime> results = build(list == null ? new ArrayList<>() : list);
            adapter.submitList(results);
            binding.emptyText.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private List<Anime> build(List<Anime> all) {
        List<Anime> out = new ArrayList<>();
        switch (mode) {
            case "favorites":
                addInOrder(out, all, LibraryStore.getFavorites(requireContext()));
                break;
            case "continue":
            case "history":
                List<String> ids = new ArrayList<>();
                for (LibraryStore.WatchEntry e : LibraryStore.getHistory(requireContext())) {
                    // Continue Watching skips anime that were watched to the end
                    if (mode.equals("continue") && e.finished) continue;
                    ids.add(e.animeId);
                }
                addInOrder(out, all, ids);
                break;
            case "airing":
                for (Anime a : all) {
                    String s = a.getStatus() == null ? "" : a.getStatus().trim();
                    if (s.equalsIgnoreCase("Ongoing") || s.equalsIgnoreCase("Airing")
                            || s.equalsIgnoreCase("Airing Now")) out.add(a);
                }
                break;
            case "completed":
                for (Anime a : all) {
                    String s = a.getStatus() == null ? "" : a.getStatus().trim();
                    if (s.equalsIgnoreCase("Completed") || s.equalsIgnoreCase("Finished")) out.add(a);
                }
                break;
        }
        return out;
    }

    /** Keeps the order of ids (newest first); anime that no longer exist are skipped. */
    private void addInOrder(List<Anime> out, List<Anime> all, List<String> ids) {
        for (String id : ids) {
            for (Anime a : all) {
                if (id.equals(a.getId())) {
                    out.add(a);
                    break;
                }
            }
        }
    }

    private void onAnimeClick(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        int destination = R.id.details;
        if (mode.equals("continue")) {
            // Resume: jump straight into the episode that was opened last
            String episodeId = LibraryStore.getLastEpisode(requireContext(), anime.getId());
            if (episodeId != null) args.putString("episodeId", episodeId);
            destination = R.id.watch;
        }
        NavHostFragment.findNavController(this).navigate(destination, args);
    }

    private static String titleFor(String mode) {
        switch (mode) {
            case "favorites": return "Favorites";
            case "continue": return "Continue Watching";
            case "airing": return "Airing Now";
            case "completed": return "Completed";
            default: return "Watch History";
        }
    }

    private static String emptyFor(String mode) {
        switch (mode) {
            case "favorites": return "No favorites yet. Tap the heart on an anime to add it.";
            case "continue": return "Nothing in progress";
            case "airing": return "Nothing is airing right now";
            case "completed": return "No completed anime yet";
            default: return "Nothing watched yet";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
