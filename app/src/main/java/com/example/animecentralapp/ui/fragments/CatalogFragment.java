package com.example.animecentralapp.ui.fragments;

import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentCatalogBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.CatalogAdapter;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every anime in the library as a plain A-Z list, with a title filter and format toggles.
 * Tap a format chip to hide that format from the list; tap it again to bring it back.
 * The choice is remembered.
 */
public class CatalogFragment extends Fragment {
    private static final String PREFS = "catalog_prefs";
    private static final String KEY_HIDDEN = "hidden_formats";

    private FragmentCatalogBinding binding;
    private CatalogAdapter adapter;
    private List<Anime> all = new ArrayList<>();
    private Set<String> hidden = new HashSet<>(); // formats switched off (lower case)

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCatalogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        loadHidden();

        adapter = new CatalogAdapter(this::openDetails);
        binding.catalogRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.catalogRecycler.setAdapter(adapter);

        binding.catalogFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        AnimeViewModel viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            all = list == null ? new ArrayList<>() : new ArrayList<>(list);
            Collections.sort(all, (x, y) -> String.valueOf(x.getTitle())
                    .compareToIgnoreCase(String.valueOf(y.getTitle())));
            buildFormatChips();
            applyFilter();
        });
    }

    // ---------- format toggles ----------

    private void loadHidden() {
        Set<String> saved = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN, null);
        hidden = saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    private void saveHidden() {
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    private static String keyOf(Anime a) {
        String f = TagUtils.formatOf(a);
        return f == null ? "" : f.trim().toLowerCase(Locale.ROOT);
    }

    /** One chip per format found in the library, with how many anime use it. */
    private void buildFormatChips() {
        if (binding == null) return;

        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, String> names = new HashMap<>();
        for (Anime a : all) {
            String key = keyOf(a);
            if (key.isEmpty()) continue;
            counts.merge(key, 1, Integer::sum);
            if (!names.containsKey(key)) names.put(key, TagUtils.formatOf(a).trim());
        }

        List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys, (x, y) -> {
            int byCount = counts.get(y) - counts.get(x);
            return byCount != 0 ? byCount : x.compareTo(y);
        });

        int scrollX = binding.catalogFormatsScroll.getScrollX();
        LinearLayout row = binding.catalogFormatRow;
        row.removeAllViews();
        binding.catalogFormatsScroll.setVisibility(keys.size() < 2 ? View.GONE : View.VISIBLE);

        for (String key : keys) {
            boolean on = !hidden.contains(key);
            TextView chip = makeChip(names.get(key) + "  " + counts.get(key), on);
            chip.setOnClickListener(v -> {
                if (!hidden.remove(key)) hidden.add(key);
                saveHidden();
                buildFormatChips();
                applyFilter();
            });
            row.addView(chip);
        }

        if (!hidden.isEmpty()) {
            TextView reset = makeChip("Show all", false);
            reset.setPaintFlags(reset.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            reset.setTextColor(ContextCompat.getColor(requireContext(), R.color.ac_text));
            reset.setOnClickListener(v -> {
                hidden.clear();
                saveHidden();
                buildFormatChips();
                applyFilter();
            });
            row.addView(reset);
        }

        binding.catalogFormatsScroll.post(() -> {
            if (binding != null) binding.catalogFormatsScroll.scrollTo(scrollX, 0);
        });
    }

    private TextView makeChip(String text, boolean on) {
        float dp = getResources().getDisplayMetrics().density;
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (34 * dp));
        lp.setMarginEnd((int) (10 * dp));
        t.setLayoutParams(lp);

        t.setBackgroundResource(on ? R.drawable.ac_fmt_on : R.drawable.ac_fmt_off);
        t.setTextColor(ContextCompat.getColor(requireContext(),
                on ? R.color.ac_on_accent : R.color.ac_text_faint));
        if (!on) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return t;
    }

    // ---------- list ----------

    private void applyFilter() {
        if (binding == null) return;
        String q = binding.catalogFilter.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<Anime> shown = new ArrayList<>();
        for (Anime a : all) {
            if (hidden.contains(keyOf(a))) continue; // its format is switched off
            String t = a.getTitle() == null ? "" : a.getTitle().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || t.contains(q)) shown.add(a);
        }
        adapter.submitList(shown);

        boolean narrowed = !q.isEmpty() || !hidden.isEmpty();
        binding.catalogTotal.setText(narrowed
                ? shown.size() + " of " + all.size() : all.size() + " anime");

        binding.catalogEmpty.setText(!hidden.isEmpty() && q.isEmpty() && shown.isEmpty()
                ? "Every format is switched off." : "No anime match that.");
        binding.catalogEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
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
