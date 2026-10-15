package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.animecentralapp.util.ListFilterBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.databinding.FragmentTagBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;

/** Shows every anime that has the chosen tag (a type like "Movie" or a genre like "Mystery"). */
public class TagFragment extends Fragment {
    private FragmentTagBinding binding;
    private AnimeAdapter adapter;
    private String tag;
    private ListFilterBar bar;
    private List<Anime> base = new ArrayList<>();

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
        tag = args != null ? args.getString("tag") : null;
        if (tag == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        binding.tagTitle.setText(tag);
        binding.backBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        adapter = new AnimeAdapter(this::openDetails);
        binding.tagResultsRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.tagResultsRecycler.setAdapter(adapter);
        bar = new ListFilterBar(requireContext(), binding.listFilter, "tag", true, this::refresh);
        bar.setHint("Search " + tag);

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            List<Anime> all = list == null ? new ArrayList<>() : list;
            base = TagUtils.filterByTag(all, tag);
            bar.setSource(base);
            refresh();
        });
    }

    private void refresh() {
        if (binding == null) return;
        List<Anime> shown = bar.filter(base);
        adapter.submitList(shown);
        bar.setVisible(!base.isEmpty());
        binding.emptyText.setText(base.isEmpty() ? "Nothing here yet" : "No anime match that.");
        binding.emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openDetails(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
