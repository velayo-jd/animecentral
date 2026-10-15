package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.animecentralapp.util.YouTubeUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentAddEpisodeBinding;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.util.AdminAccess;
import com.example.animecentralapp.util.VimeoUtil;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Add (or edit / delete) one episode of an anime: anime/{animeId}/episodes/{id}.
 * Opened with a Bundle: animeId, and episodeId when editing.
 * Paste a Vimeo link and the video id, the unlisted hash and the thumbnail are worked out for you.
 */
public class AddEpisodeFragment extends Fragment {
    private FragmentAddEpisodeBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String animeId;
    private String episodeId;          // null = adding
    private CollectionReference episodesRef;
    private final Set<Integer> takenNumbers = new HashSet<>();
    private boolean thumbWasAutoFilled = false;
    private boolean fillingProgrammatically = false;
    private Runnable pendingLookup;
    private boolean saving = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEpisodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        animeId = args != null ? args.getString("animeId") : null;
        episodeId = args != null ? args.getString("episodeId") : null;
        if (!AdminAccess.isAdmin() || animeId == null) {
            Toast.makeText(requireContext(), "Not allowed", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        episodesRef = db.collection("anime").document(animeId).collection("episodes");

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        boolean editing = episodeId != null;
        binding.formTitle.setText(editing ? "Edit episode" : "Add episode");
        binding.saveBtn.setText(editing ? "Save changes" : "Add episode");
        binding.deleteBtn.setVisibility(editing ? View.VISIBLE : View.GONE);

        db.collection("anime").document(animeId).get().addOnSuccessListener(d -> {
            if (binding == null) return;
            String t = d.getString("title");
            binding.formSubtitle.setText(t == null ? "" : t);
        });

        binding.videoInput.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { onVideoTextChanged(); }
        });
        binding.thumbInput.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!fillingProgrammatically) thumbWasAutoFilled = false; // the user typed it
                showThumbPreview(s.toString().trim());
            }
        });
        binding.saveBtn.setOnClickListener(v -> save());
        binding.deleteBtn.setOnClickListener(v -> confirmDelete());

        loadExisting();
    }

    /** Learn which numbers are taken (and the next free one); in edit mode also fill the form. */
    private void loadExisting() {
        episodesRef.get().addOnSuccessListener(snap -> {
            if (binding == null) return;
            int max = 0;
            DocumentSnapshot editing = null;
            for (DocumentSnapshot d : snap.getDocuments()) {
                Object raw = d.get("number");
                Integer n = null;
                if (raw instanceof Number) n = ((Number) raw).intValue();
                else if (raw instanceof String) {
                    try { n = Integer.parseInt(((String) raw).trim()); } catch (NumberFormatException ignored) { }
                }
                if (d.getId().equals(episodeId)) { editing = d; continue; }
                if (n != null) {
                    takenNumbers.add(n);
                    max = Math.max(max, n);
                }
            }
            if (episodeId == null) {
                binding.episodeNumber.setText(String.valueOf(max + 1)); // next free number
            } else if (editing != null) {
                fillForm(editing);
            }
        });
    }

    private void fillForm(DocumentSnapshot d) {
        Object rawNumber = d.get("number");
        if (rawNumber != null) binding.episodeNumber.setText(String.valueOf(rawNumber));
        String title = d.getString("title");
        if (title != null) binding.episodeTitle.setText(title);
        String id = d.getString("vimeoId");
        String hash = d.getString("vimeoHash");
        if (id != null && !id.isEmpty()) {
            fillingProgrammatically = true;
            binding.videoInput.setText("https://vimeo.com/" + id
                    + (hash == null || hash.isEmpty() ? "" : "/" + hash));
            fillingProgrammatically = false;
        }
        String thumb = d.getString("thumbnailUrl");
        if (thumb != null) {
            fillingProgrammatically = true;
            binding.thumbInput.setText(thumb);
            fillingProgrammatically = false;
        }
    }

    // ---------- Vimeo link handling ----------

    private void onVideoTextChanged() {
        if (binding == null) return;
        String text = binding.videoInput.getText().toString();
        VimeoUtil.Parsed p = VimeoUtil.parse(text);
        String yt = p == null ? YouTubeUtil.parseId(text) : null;   // Vimeo is tried first

        if (text.trim().isEmpty()) {
            setVideoStatus("Paste a Vimeo or YouTube link, or the video ID.", false);
        } else if (p != null) {
            setVideoStatus("Vimeo video " + p.id
                    + (p.hash != null ? " (unlisted link detected)" : ""), false);
        } else if (yt != null) {
            setVideoStatus("YouTube video " + yt, false);
        } else {
            setVideoStatus("That doesn't look like a Vimeo or YouTube link.", true);
        }

        if (pendingLookup != null) main.removeCallbacks(pendingLookup);
        if (fillingProgrammatically) return;
        if (yt != null) { fillYoutubeThumbnail(yt); return; }
        if (p == null) return;
        pendingLookup = () -> lookupThumbnail(p);
        main.postDelayed(pendingLookup, 600);
    }

    /** Same rule as the Vimeo lookup: don't overwrite a thumbnail the user typed themselves. */
    private void fillYoutubeThumbnail(String id) {
        String current = binding.thumbInput.getText().toString().trim();
        if (!current.isEmpty() && !thumbWasAutoFilled) return;
        fillingProgrammatically = true;
        binding.thumbInput.setText(YouTubeUtil.thumbnailUrl(id));
        fillingProgrammatically = false;
        thumbWasAutoFilled = true;
    }

    private void setVideoStatus(String text, boolean error) {
        binding.videoStatus.setText(text);
        binding.videoStatus.setTextColor(error
                ? android.graphics.Color.parseColor("#E5484D")
                : ContextCompat.getColor(requireContext(), R.color.ac_text_dim));
    }

    /** Ask Vimeo for the thumbnail, unless the user already typed their own. */
    private void lookupThumbnail(VimeoUtil.Parsed p) {
        if (binding == null) return;
        String current = binding.thumbInput.getText().toString().trim();
        if (!current.isEmpty() && !thumbWasAutoFilled) return;
        VimeoUtil.fetchThumbnail(p, url -> {
            if (binding == null || url == null) return;

            // The field may have changed to another video while Vimeo was answering
            VimeoUtil.Parsed still = VimeoUtil.parse(binding.videoInput.getText().toString());
            if (still == null || !still.id.equals(p.id)) return;

            String now = binding.thumbInput.getText().toString().trim();
            if (!now.isEmpty() && !thumbWasAutoFilled) return;
            fillingProgrammatically = true;
            binding.thumbInput.setText(url);
            fillingProgrammatically = false;
            thumbWasAutoFilled = true;
        });
    }

    private void showThumbPreview(String url) {
        if (binding == null) return;
        if (url.isEmpty()) {
            binding.thumbPreviewCard.setVisibility(View.GONE);
            return;
        }
        binding.thumbPreviewCard.setVisibility(View.VISIBLE);
        Glide.with(this).load(url).into(binding.thumbPreview);
    }

    // ---------- save / delete ----------

    private void save() {
        if (saving) return;

        int number;
        try {
            number = Integer.parseInt(binding.episodeNumber.getText().toString().trim());
        } catch (NumberFormatException e) {
            number = 0;
        }
        if (number <= 0) {
            binding.episodeNumber.setError("Enter the episode number");
            return;
        }
        if (takenNumbers.contains(number)) {
            binding.episodeNumber.setError("Episode " + number + " already exists");
            return;
        }

        // Vimeo is tried first, then YouTube
        String raw = binding.videoInput.getText().toString();
        VimeoUtil.Parsed p = VimeoUtil.parse(raw);
        String yt = p == null ? YouTubeUtil.parseId(raw) : null;
        if (p == null && yt == null) {
            binding.videoInput.setError("Paste a Vimeo or YouTube link or ID");
            return;
        }

        boolean editing = episodeId != null;
        String title = binding.episodeTitle.getText().toString().trim();
        String thumb = binding.thumbInput.getText().toString().trim();

        Map<String, Object> data = new HashMap<>();
        data.put("number", number);
        // Optional fields: empty means "leave out" when adding, "remove" when editing
        if (yt != null) {
            data.put("youtubeId", yt);
            data.put("vimeoId", editing ? FieldValue.delete() : null);     // switch source cleanly
            data.put("vimeoHash", editing ? FieldValue.delete() : null);
        } else {
            data.put("vimeoId", p.id);
            data.put("vimeoHash", p.hash != null ? p.hash : (editing ? FieldValue.delete() : null));
            data.put("youtubeId", editing ? FieldValue.delete() : null);
        }
        data.put("title", !title.isEmpty() ? title : (editing ? FieldValue.delete() : null));
        data.put("thumbnailUrl", !thumb.isEmpty() ? thumb : (editing ? FieldValue.delete() : null));
        if (!editing) data.values().removeIf(v -> v == null);

        saving = true;
        binding.saveBtn.setEnabled(false);
        com.google.android.gms.tasks.Task<Void> task = editing
                ? episodesRef.document(episodeId).update(data)
                : episodesRef.document().set(data);
        task.addOnSuccessListener(v -> {
            if (getContext() == null) return;
            Toast.makeText(getContext(), editing ? "Episode saved" : "Episode added",
                    Toast.LENGTH_SHORT).show();
            if (binding != null) NavHostFragment.findNavController(this).popBackStack();
        }).addOnFailureListener(e -> {
            saving = false;
            if (binding != null) binding.saveBtn.setEnabled(true);
            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Couldn't save. Check your Firestore rules. (" + e.getMessage() + ")",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete this episode?")
                .setMessage("It disappears from the app for everyone. This can't be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        episodesRef.document(episodeId).delete()
                                .addOnSuccessListener(v -> {
                                    if (getContext() == null) return;
                                    Toast.makeText(getContext(), "Episode deleted",
                                            Toast.LENGTH_SHORT).show();
                                    if (binding != null) {
                                        NavHostFragment.findNavController(this).popBackStack();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (getContext() != null) Toast.makeText(getContext(),
                                            "Couldn't delete: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingLookup != null) main.removeCallbacks(pendingLookup);
        binding = null;
    }

    /** TextWatcher with only afterTextChanged to fill in. */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
    }
}
