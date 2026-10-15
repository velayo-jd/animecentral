package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentImportBinding;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.ImportAdapter;
import com.example.animecentralapp.util.AdminAccess;
import com.example.animecentralapp.util.AniListClient;
import com.example.animecentralapp.util.AniListClient.Media;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Import from AniList: search a show, tap it, and its title, synopsis, genres, status, episode count,
 * year and poster are saved to Firestore (anime/{id}). Existing shows are matched by AniList id or by
 * title and just get their info refreshed; their episodes and "recommended" flag are left alone.
 */
public class ImportFragment extends Fragment {
    private FragmentImportBinding binding;
    private ImportAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int searchSeq = 0;

    private List<Media> results = new ArrayList<>();
    // What's already in the library: AniList id -> doc id, and lower-case title -> doc id
    private final Map<Integer, String> docByAniListId = new HashMap<>();
    private final Map<String, String> docByTitle = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!AdminAccess.isAdmin()) {
            Toast.makeText(requireContext(), "Importing is only for admin accounts",
                    Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        adapter = new ImportAdapter(this::confirm);
        binding.importRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.importRecycler.setAdapter(adapter);

        binding.importQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                final String q = s.toString();
                pendingSearch = () -> runSearch(q);
                main.postDelayed(pendingSearch, 450); // wait until typing pauses
            }
        });
        binding.importQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                runSearch(binding.importQuery.getText().toString());
                return true;
            }
            return false;
        });

        loadLibrary();
        runSearch(""); // popular shows to start with
    }

    /** Remember which AniList shows are already in Firestore. */
    private void loadLibrary() {
        db.collection("anime").get().addOnSuccessListener(snap -> {
            if (binding == null) return;
            docByAniListId.clear();
            docByTitle.clear();
            for (QueryDocumentSnapshot d : snap) {
                Object al = d.get("anilistId");
                Integer alId = null;
                if (al instanceof Number) {
                    alId = ((Number) al).intValue();
                } else if (al instanceof String) {
                    try { alId = Integer.parseInt(((String) al).trim()); } catch (NumberFormatException ignored) { }
                }
                if (alId != null) docByAniListId.put(alId, d.getId());
                String title = d.getString("title");
                if (title != null) docByTitle.put(norm(title), d.getId());
            }
            refreshExisting();
        });
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private String existingDocId(Media m) {
        String id = docByAniListId.get(m.id);
        if (id == null && m.title != null) id = docByTitle.get(norm(m.title));
        if (id == null && m.altTitle != null) id = docByTitle.get(norm(m.altTitle));
        return id;
    }

    private void refreshExisting() {
        if (adapter == null) return;
        Set<Integer> ids = new HashSet<>();
        for (Media m : results) if (existingDocId(m) != null) ids.add(m.id);
        adapter.setExisting(ids);
    }

    private void runSearch(String query) {
        if (binding == null) return;
        final int seq = ++searchSeq;
        binding.importProgress.setVisibility(View.VISIBLE);
        binding.importMessage.setVisibility(View.GONE);
        AniListClient.search(query, (list, error) -> {
            if (binding == null || seq != searchSeq) return; // an older search finished late
            binding.importProgress.setVisibility(View.GONE);
            if (error != null) {
                binding.importMessage.setText(error);
                binding.importMessage.setVisibility(View.VISIBLE);
                return;
            }
            results = list == null ? new ArrayList<>() : list;
            adapter.submitList(new ArrayList<>(results));
            refreshExisting();
            binding.importMessage.setText("No results");
            binding.importMessage.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    // ---------- confirm + save ----------

    private void confirm(Media m) {
        boolean has = existingDocId(m) != null;
        String synopsis = m.synopsis == null ? "" : m.synopsis;
        if (synopsis.length() > 260) synopsis = synopsis.substring(0, 260).trim() + "…";
        String msg = m.summaryLine()
                + (m.genres.isEmpty() ? "" : "\n" + android.text.TextUtils.join(", ", m.genres))
                + (synopsis.isEmpty() ? "" : "\n\n" + synopsis)
                + (has ? "\n\nThis refreshes the show's info. Episodes are kept."
                       : "\n\nEpisodes are added separately (their video links).");
        new AlertDialog.Builder(requireContext())
                .setTitle(m.title)
                .setMessage(msg)
                .setPositiveButton(has ? "Update" : "Add", (d, w) -> save(m))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save(Media m) {
        String existing = existingDocId(m);
        String docId = existing != null ? existing : "al" + m.id;
        Map<String, Object> data = m.toFirestoreMap();
        if (existing == null) data.put("recommended", false);

        db.collection("anime").document(docId).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    if (binding == null) return;
                    docByAniListId.put(m.id, docId);
                    if (m.title != null) docByTitle.put(norm(m.title), docId);
                    refreshExisting();
                    Toast.makeText(requireContext(),
                            existing == null ? "Added " + m.title : "Updated " + m.title,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (getContext() == null) return;
                    Toast.makeText(getContext(),
                            "Couldn't save. Check your Firestore rules. (" + e.getMessage() + ")",
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) main.removeCallbacks(pendingSearch);
        binding = null;
    }
}
