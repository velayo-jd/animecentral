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
import com.example.animecentralapp.databinding.FragmentAllTagsBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.TagAdapter;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;

/** Lists every genre, or every type, as boxes. Tapping one opens the anime with that tag. */
public class AllTagsFragment extends Fragment {
    private FragmentAllTagsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAllTagsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String kind = args != null ? args.getString("kind", "genre") : "genre";
        boolean genres = !"type".equals(kind);

        binding.allTagsTitle.setText(genres ? "All Genres" : "All Types");
        binding.backBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        TagAdapter adapter = new TagAdapter(tag -> {
            Bundle b = new Bundle();
            b.putString("tag", tag);
            NavHostFragment.findNavController(this).navigate(R.id.tag_browse, b);
        });
        binding.allTagsRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.allTagsRecycler.setAdapter(adapter);

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            List<Anime> all = list == null ? new ArrayList<>() : list;
            List<String> tags = genres ? TagUtils.collectGenres(all) : TagUtils.collectTypes(all);
            adapter.submitList(tags);
            binding.emptyText.setVisibility(tags.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
