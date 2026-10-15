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

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentHistoryBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;

/** History tab: Watch History, Continue Watching (unfinished only) and Favorites. */
public class HistoryFragment extends Fragment {
    private FragmentHistoryBinding binding;

    private View savedSection;
    private List<Anime> lastAll = new ArrayList<>();

    private static class Row {
        final String mode;
        final String emptyText;
        final AnimeAdapter adapter;
        final View root;
        final RecyclerView recycler;
        final TextView empty;

        Row(String mode, String emptyText, AnimeAdapter adapter, View root,
            RecyclerView recycler, TextView empty) {
            this.mode = mode;
            this.emptyText = emptyText;
            this.adapter = adapter;
            this.root = root;
            this.recycler = recycler;
            this.empty = empty;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        rows.clear();
        addRow("Watch History", "history", "Nothing watched yet");
        addRow("Continue Watching", "continue", "Nothing in progress");
        addRow("Favorites", "favorites", "No favorites yet. Tap the heart on an anime.");
        addRow("Saved Anime", "saved", "Nothing saved yet. Tap the bookmark on an anime.");

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        savedSection = com.example.animecentralapp.ui.SavedEpisodesSection
                .create(requireContext(), binding.historySections);
        binding.historySections.addView(savedSection);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            lastAll = list == null ? new ArrayList<>() : list;
            refresh(lastAll);
        });
    }

    private void addRow(String title, String mode, String emptyText) {
        View section = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_home_section, binding.historySections, false);
        ((TextView) section.findViewById(R.id.section_title)).setText(title);
        section.findViewById(R.id.section_see_all).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("mode", mode);
            NavHostFragment.findNavController(this).navigate(R.id.collection, args);
        });

        AnimeAdapter adapter = new AnimeAdapter(anime -> open(anime, mode), 190, 130);
        RecyclerView recycler = section.findViewById(R.id.section_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(adapter);

        binding.historySections.addView(section);
        rows.add(new Row(mode, emptyText, adapter, section, recycler,
                section.findViewById(R.id.section_empty)));
    }

    private void reloadSaved() {
        if (savedSection == null || getContext() == null) return;
        com.example.animecentralapp.ui.SavedEpisodesSection.load(requireContext(), savedSection, lastAll,
                (anime, episodeId) -> {
                    Bundle args = new Bundle();
                    args.putString("animeId", anime.getId());
                    if (episodeId != null) args.putString("episodeId", episodeId);
                    NavHostFragment.findNavController(this)
                            .navigate(episodeId == null ? R.id.details : R.id.watch, args);
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && !rows.isEmpty()) refresh(lastAll);
    }

    private void refresh(List<Anime> all) {
        lastAll = all;
        reloadSaved();
        List<LibraryStore.WatchEntry> history = LibraryStore.getHistory(requireContext());
        List<String> favorites = LibraryStore.getFavorites(requireContext());
        List<String> saved = LibraryStore.getSaved(requireContext());

        for (Row row : rows) {
            List<String> ids = new ArrayList<>();
            switch (row.mode) {
                case "favorites":
                    ids.addAll(favorites);
                    break;
                case "saved":
                    ids.addAll(saved);
                    break;
                case "continue":
                    for (LibraryStore.WatchEntry e : history) if (!e.finished) ids.add(e.animeId);
                    break;
                default:
                    for (LibraryStore.WatchEntry e : history) ids.add(e.animeId);
            }

            List<Anime> items = new ArrayList<>();
            for (String id : ids) {
                for (Anime a : all) {
                    if (id.equals(a.getId())) {
                        items.add(a);
                        break;
                    }
                }
            }
            row.adapter.submitList(items);
            row.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            row.empty.setText(row.emptyText);
            row.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

            if (row.mode.equals("history")) {
                List<String> cont = new ArrayList<>();
                for (LibraryStore.WatchEntry e : history) if (!e.finished) cont.add(e.animeId);
                List<String> all2 = new ArrayList<>();
                for (LibraryStore.WatchEntry e : history) all2.add(e.animeId);
                row.root.setVisibility(all2.equals(cont) ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void open(Anime anime, String mode) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        int destination = R.id.details;
        if (mode.equals("continue")) {
            // Resume where the user left off
            String episodeId = LibraryStore.getLastEpisode(requireContext(), anime.getId());
            if (episodeId != null) args.putString("episodeId", episodeId);
            destination = R.id.watch;
        }
        NavHostFragment.findNavController(this).navigate(destination, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
