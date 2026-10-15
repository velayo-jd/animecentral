package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentAddAnimeBinding;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.util.AdminAccess;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Admin-only form that creates a new document in "anime".
 * Episodes are added afterwards from the Details page.
 */
public class AddAnimeFragment extends Fragment {
    private static final String[] TYPES = {"Anime", "Movie", "OVA"};
    private static final String[] STATUSES = {"Completed", "Airing", "Upcoming"};

    private FragmentAddAnimeBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String type = TYPES[0];
    private String status = STATUSES[0];
    private boolean saving = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddAnimeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // The UI hides this page, but check again in case someone navigates here some other way
        if (!AdminAccess.isAdmin()) {
            Toast.makeText(requireContext(), "Adding anime is only for admin accounts",
                    Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        binding.animeType.setOnClickListener(v -> pick("Format", TYPES, type, choice -> {
            type = choice;
            binding.animeType.setText(choice + "  ▾");
        }));
        binding.animeStatus.setOnClickListener(v -> pick("Status", STATUSES, status, choice -> {
            status = choice;
            binding.animeStatus.setText(choice + "  ▾");
        }));

        binding.saveAnimeBtn.setOnClickListener(v -> validateAndSave());
    }

    private interface OnPick { void picked(String choice); }

    private void pick(String title, String[] options, String current, OnPick callback) {
        int checked = 0;
        for (int i = 0; i < options.length; i++) if (options[i].equals(current)) checked = i;
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    callback.picked(options[which]);
                    d.dismiss();
                })
                .show();
    }

    private void validateAndSave() {
        if (saving || binding == null) return;

        String title = text(binding.animeTitle);
        if (title.isEmpty()) {
            binding.animeTitle.setError("Title is required");
            binding.animeTitle.requestFocus();
            return;
        }

        String poster = text(binding.animePoster);
        String banner = text(binding.animeBanner);
        if (!poster.isEmpty() && !poster.startsWith("http")) {
            binding.animePoster.setError("Must start with http");
            return;
        }
        if (!banner.isEmpty() && !banner.startsWith("http")) {
            binding.animeBanner.setError("Must start with http");
            return;
        }

        int year = parseInt(text(binding.animeYear));
        if (year != 0 && (year < 1900 || year > 2100)) {
            binding.animeYear.setError("Check the year");
            return;
        }

        saving = true;
        binding.saveAnimeBtn.setAlpha(0.5f);

        // Warn before creating a duplicate title
        db.collection("anime").whereEqualTo("title", title).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (binding == null) return;
                    if (snap.isEmpty()) {
                        save(title, poster, banner, year);
                    } else {
                        stopSaving();
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Already in the library")
                                .setMessage("\"" + title + "\" already exists. Add another copy anyway?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Add anyway", (d, w) -> {
                                    saving = true;
                                    binding.saveAnimeBtn.setAlpha(0.5f);
                                    save(title, poster, banner, year);
                                })
                                .show();
                    }
                })
                // If the check itself fails (offline?), still try to save
                .addOnFailureListener(e -> {
                    if (binding != null) save(title, poster, banner, year);
                });
    }

    private void save(String title, String poster, String banner, int year) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("tagline", text(binding.animeTagline));
        data.put("synopsis", text(binding.animeSynopsis));
        data.put("genres", parseGenres(text(binding.animeGenres)));
        data.put("type", type);
        data.put("status", status);
        data.put("episodes", parseInt(text(binding.animeEpisodes)));
        data.put("year", year);
        data.put("posterUrl", poster);
        data.put("thumbnailUrl", banner);
        data.put("recommended", binding.animeRecommended.isChecked());
        data.put("addedAt", FieldValue.serverTimestamp());

        db.collection("anime").add(data)
                .addOnSuccessListener(ref -> {
                    if (getContext() == null) return;
                    Toast.makeText(requireContext(), "\"" + title + "\" added",
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).popBackStack();
                })
                .addOnFailureListener(e -> {
                    stopSaving();
                    if (getContext() == null) return;
                    // Usually a Firestore rules problem: your email must be in the rules list too
                    Toast.makeText(requireContext(), "Couldn't save: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void stopSaving() {
        saving = false;
        if (binding != null) binding.saveAnimeBtn.setAlpha(1f);
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /** "action, Fantasy ,  " -> ["Action", "Fantasy"] (trimmed, capitalised, no duplicates) */
    private static List<String> parseGenres(String raw) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : raw.split(",")) {
            String g = part.trim();
            if (g.isEmpty()) continue;
            g = g.substring(0, 1).toUpperCase(Locale.ROOT) + g.substring(1);
            if (seen.add(g.toLowerCase(Locale.ROOT))) out.add(g);
        }
        return out;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}