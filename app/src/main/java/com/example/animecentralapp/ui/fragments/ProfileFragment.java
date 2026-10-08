package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentProfileBinding;
import com.example.animecentralapp.databinding.ItemProfileRowBinding;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.util.LibraryStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Profile / "Me" page: avatar plus seven rows - name, email, your library counts
 * (each opens its list), clear history, and sign in / out.
 */
public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        render();
    }

    /** Rebuilds the avatar and all rows (called again after anything changes). */
    private void render() {
        if (binding == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null;

        String name = displayName(user);
        binding.profileAvatar.setText(name.substring(0, 1).toUpperCase());
        binding.profileRows.removeAllViews();

        int favorites = LibraryStore.getFavorites(requireContext()).size();
        int watched = LibraryStore.getHistory(requireContext()).size();
        int inProgress = 0;
        for (LibraryStore.WatchEntry e : LibraryStore.getHistory(requireContext())) {
            if (!e.finished) inProgress++;
        }

        // 1. Name (tap to change)
        addRow("Name", name, true, this::showEditName);
        // 2. Email
        addRow("Email", signedIn && user.getEmail() != null ? user.getEmail() : "Not signed in",
                false, null);
        // 3-5. Library
        addRow("Favorites", String.valueOf(favorites), true, () -> openList("favorites"));
        addRow("Watch History", String.valueOf(watched), true, () ->
                NavHostFragment.findNavController(this).navigate(R.id.history));
        addRow("Continue Watching", String.valueOf(inProgress), true, () -> openList("continue"));
        // 6. Clear history
        addRow("Clear Watch History", "", true, this::confirmClearHistory);
        // 7. Sign in / out
        if (signedIn) {
            addRow("Sign Out", "", true, () -> {
                FirebaseAuth.getInstance().signOut();
                LibraryStore.setName(requireContext(), "");
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show();
                // Back to the log in screen
                NavHostFragment.findNavController(this).navigate(R.id.login, null,
                        new androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true).build());
            });
        } else {
            addRow("Sign In", "", true, () ->
                    NavHostFragment.findNavController(this).navigate(R.id.login));
        }
    }

    private void addRow(String label, String value, boolean arrow, @Nullable Runnable onClick) {
        ItemProfileRowBinding row = ItemProfileRowBinding.inflate(
                getLayoutInflater(), binding.profileRows, false);
        row.rowLabel.setText(label);
        row.rowValue.setText(value);
        row.rowArrow.setVisibility(arrow ? View.VISIBLE : View.GONE);
        if (onClick != null) row.getRoot().setOnClickListener(v -> onClick.run());
        binding.profileRows.addView(row.getRoot());
    }

    /** Name chosen on this page, else the account name, else "Guest". */
    private String displayName(@Nullable FirebaseUser user) {
        return LibraryStore.displayName(requireContext(),
                user != null ? user.getDisplayName() : null);
    }

    private void showEditName() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine();
        input.setText(LibraryStore.getName(requireContext()));
        input.setSelection(input.getText().length());
        input.setHint("Your name");

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change name")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    LibraryStore.setName(requireContext(), input.getText().toString());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear watch history?")
                .setMessage("This also empties Continue Watching. Favorites are kept.")
                .setPositiveButton("Clear", (d, w) -> {
                    LibraryStore.clearHistory(requireContext());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openList(String mode) {
        Bundle args = new Bundle();
        args.putString("mode", mode);
        NavHostFragment.findNavController(this).navigate(R.id.collection, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
