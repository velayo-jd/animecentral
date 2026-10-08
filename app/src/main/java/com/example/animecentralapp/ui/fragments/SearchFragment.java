package com.example.animecentralapp.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.databinding.FragmentSearchBinding;
import com.example.animecentralapp.databinding.ItemSearchRowBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search screen: live filtering while typing, recent searches (stored on the phone)
 * and recently searched anime (the anime opened from search, also stored on the phone).
 */
public class SearchFragment extends Fragment {
    private static final String PREFS = "search_prefs";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_RECENT = 7;
    private static final String KEY_RECENT_ANIME = "recent_anime";
    private static final int MAX_RECENT_ANIME = 5;

    private FragmentSearchBinding binding;
    private AnimeViewModel viewModel;
    private AnimeAdapter adapter;
    private List<Anime> allAnime = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);

        adapter = new AnimeAdapter(this::openDetails);
        binding.searchResultsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.searchResultsRecyclerView.setAdapter(adapter);

        setupInput();

        // Search runs on the full list in memory, so it works on any field value / status
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            allAnime = list == null ? new ArrayList<>() : list;
            applyQuery();
        });

        binding.clearRecent.setOnClickListener(v -> {
            prefs().edit().remove(KEY_RECENT).apply();
            renderRecent();
        });

        renderRecent();
        renderRecentAnime();

        // Opened from the Watch screen's search box with a query already typed
        Bundle args = getArguments();
        String startQuery = args != null ? args.getString("query") : null;
        if (startQuery != null && !startQuery.trim().isEmpty()) {
            binding.searchInput.setText(startQuery.trim());
            binding.searchInput.setSelection(binding.searchInput.getText().length());
            saveRecent(startQuery);
        } else {
            focusInput();
        }
    }

    private void setupInput() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyQuery(); }
        });

        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            saveRecent(currentQuery());
            hideKeyboard();
            return true;
        });

        binding.topBar.searchIcon.setOnClickListener(v -> focusInput());
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
    }

    private String currentQuery() {
        return binding.searchInput.getText().toString().trim();
    }

    /** Empty field -> suggestions; otherwise -> filtered results. */
    private void applyQuery() {
        if (binding == null) return;

        String query = currentQuery().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            binding.suggestionsScroll.setVisibility(View.VISIBLE);
            binding.searchResultsRecyclerView.setVisibility(View.GONE);
            binding.noResultsText.setVisibility(View.GONE);
            return;
        }

        List<Anime> results = new ArrayList<>();
        for (Anime anime : allAnime) {
            String title = anime.getTitle();
            if (title != null && title.toLowerCase(Locale.ROOT).contains(query)) {
                results.add(anime);
            }
        }
        adapter.submitList(results);

        binding.suggestionsScroll.setVisibility(View.GONE);
        boolean has = !results.isEmpty();
        binding.searchResultsRecyclerView.setVisibility(has ? View.VISIBLE : View.GONE);
        binding.noResultsText.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    // ---------- Recent searches (SharedPreferences) ----------

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private List<String> getRecents() {
        List<String> list = new ArrayList<>();
        String raw = prefs().getString(KEY_RECENT, "");
        for (String s : raw.split("\n")) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list;
    }

    private void saveRecent(String query) {
        if (query == null || query.trim().isEmpty()) return;
        String q = query.trim();

        List<String> list = getRecents();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(q)) list.remove(i);
        }
        list.add(0, q);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);

        prefs().edit().putString(KEY_RECENT, android.text.TextUtils.join("\n", list)).apply();
        renderRecent();
    }

    /** The X next to a recent search: deletes only that one. */
    private void removeRecent(String query) {
        List<String> list = getRecents();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(query)) list.remove(i);
        }
        prefs().edit().putString(KEY_RECENT, android.text.TextUtils.join("\n", list)).apply();
        renderRecent();
    }

    private void renderRecent() {
        if (binding == null) return;
        List<String> recents = getRecents();
        binding.recentContainer.removeAllViews();
        binding.recentSection.setVisibility(recents.isEmpty() ? View.GONE : View.VISIBLE);

        for (String q : recents) {
            ItemSearchRowBinding row = ItemSearchRowBinding.inflate(
                    getLayoutInflater(), binding.recentContainer, false);
            row.rowText.setText(q);
            row.rowText.setOnClickListener(v -> {
                binding.searchInput.setText(q);
                binding.searchInput.setSelection(q.length());
                hideKeyboard();
            });
            row.rowRemove.setOnClickListener(v -> removeRecent(q));
            binding.recentContainer.addView(row.getRoot());
        }
    }

    // ---------- Recently searched anime (SharedPreferences) ----------

    /** Saved as one "id<TAB>title" per line, newest first. */
    private List<String[]> getRecentAnime() {
        List<String[]> out = new ArrayList<>();
        String raw = prefs().getString(KEY_RECENT_ANIME, "");
        if (raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 2);
            if (parts.length == 2 && !parts[0].isEmpty()) out.add(parts);
        }
        return out;
    }

    private void saveRecentAnime(Anime anime) {
        if (anime.getId() == null || anime.getTitle() == null) return;
        List<String[]> list = getRecentAnime();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i)[0].equals(anime.getId())) {
                list.remove(i);
                break;
            }
        }
        list.add(0, new String[]{anime.getId(), anime.getTitle().replace("\n", " ").replace("\t", " ")});
        while (list.size() > MAX_RECENT_ANIME) list.remove(list.size() - 1);

        List<String> lines = new ArrayList<>();
        for (String[] e : list) lines.add(e[0] + "\t" + e[1]);
        prefs().edit().putString(KEY_RECENT_ANIME, android.text.TextUtils.join("\n", lines)).apply();
    }

    /** The X next to a recently searched anime: deletes only that one. */
    private void removeRecentAnime(String animeId) {
        List<String[]> list = getRecentAnime();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i)[0].equals(animeId)) list.remove(i);
        }
        List<String> lines = new ArrayList<>();
        for (String[] e : list) lines.add(e[0] + "\t" + e[1]);
        prefs().edit().putString(KEY_RECENT_ANIME, android.text.TextUtils.join("\n", lines)).apply();
        renderRecentAnime();
    }

    private void renderRecentAnime() {
        if (binding == null) return;
        List<String[]> list = getRecentAnime();
        binding.popularContainer.removeAllViews();
        binding.popularSection.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);

        for (String[] entry : list) {
            String id = entry[0];
            ItemSearchRowBinding row = ItemSearchRowBinding.inflate(
                    getLayoutInflater(), binding.popularContainer, false);
            row.rowText.setText(entry[1]);
            row.rowText.setOnClickListener(v -> openDetailsById(id));
            row.rowRemove.setOnClickListener(v -> removeRecentAnime(id));
            binding.popularContainer.addView(row.getRoot());
        }
    }

    // ---------- Navigation / helpers ----------

    private void openDetails(Anime anime) {
        saveRecent(currentQuery());
        saveRecentAnime(anime);
        openDetailsById(anime.getId());
    }

    private void openDetailsById(String animeId) {
        Bundle args = new Bundle();
        args.putString("animeId", animeId);
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    private void focusInput() {
        if (binding == null) return;
        binding.searchInput.requestFocus();
        binding.searchInput.post(() -> {
            if (binding == null || getContext() == null) return;
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard() {
        if (binding == null || getContext() == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
        binding.searchInput.clearFocus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
