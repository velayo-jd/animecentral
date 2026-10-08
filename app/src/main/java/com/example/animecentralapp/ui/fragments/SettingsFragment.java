package com.example.animecentralapp.ui.fragments;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentSettingsBinding;
import com.example.animecentralapp.databinding.ItemSettingsRowBinding;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.util.AppSettings;
import com.example.animecentralapp.util.LibraryStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Settings tab: account banner (opens Profile), then two cards of settings.
 */
public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        // Banner -> Profile
        binding.accountBanner.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.profile));

        // Section 1: Playback & Data
        addSwitchRow(binding.sectionOneRows, "Light mode",
                AppSettings.isLight(requireContext()), on -> {
                    AppSettings.setLight(requireContext(), on);
                    // The app restarts its screens in the new look right away
                    AppCompatDelegate.setDefaultNightMode(on
                            ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
                });
        addSwitchRow(binding.sectionOneRows, "Autoplay next episode",
                AppSettings.isAutoplay(requireContext()),
                on -> AppSettings.setAutoplay(requireContext(), on));
        addRow(binding.sectionOneRows, "Clear Search History", "", this::confirmClearSearch);
        addRow(binding.sectionOneRows, "Clear Watch History", "", this::confirmClearWatch);

        // Section 2: About
        addRow(binding.sectionTwoRows, "Version", versionName(), null);
        addRow(binding.sectionTwoRows, "About Anime Central", "›", () -> showInfo(
                "Anime Central",
                "A school project: an Android anime streaming app built with Java, "
                        + "Firebase, and Vimeo / Rumble players."));
        addRow(binding.sectionTwoRows, "Video Sources", "›", () -> showInfo(
                "Video Sources",
                "Videos are played from Vimeo and Rumble. They are streamed from those "
                        + "services and are not stored in this app."));
        addRow(binding.sectionTwoRows, "Content Notice", "›", () -> showInfo(
                "Content Notice",
                "Only content that its owner has the right to share should be added to "
                        + "this app."));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBanner(); // the name may have been changed on the Profile page
    }

    private void refreshBanner() {
        if (binding == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = LibraryStore.displayName(requireContext(),
                user != null ? user.getDisplayName() : null);
        binding.bannerName.setText(name);
        binding.bannerAvatar.setText(name.substring(0, 1).toUpperCase());
    }

    // ---------- rows ----------

    private interface SwitchListener { void onChanged(boolean on); }

    private void addRow(LinearLayout parent, String label, String value, @Nullable Runnable onClick) {
        ItemSettingsRowBinding row = ItemSettingsRowBinding.inflate(
                getLayoutInflater(), parent, false);
        row.rowLabel.setText(label);
        row.rowValue.setText(value);
        if (onClick != null) {
            row.getRoot().setOnClickListener(v -> onClick.run());
        } else {
            row.getRoot().setClickable(false);
        }
        parent.addView(row.getRoot());
    }

    private void addSwitchRow(LinearLayout parent, String label, boolean checked,
                              SwitchListener listener) {
        ItemSettingsRowBinding row = ItemSettingsRowBinding.inflate(
                getLayoutInflater(), parent, false);
        row.rowLabel.setText(label);
        row.rowValue.setVisibility(View.GONE);
        row.rowSwitch.setVisibility(View.VISIBLE);
        row.rowSwitch.setChecked(checked);
        row.rowSwitch.setSaveEnabled(false);
        row.rowSwitch.setOnCheckedChangeListener((b, on) -> listener.onChanged(on));
        row.getRoot().setOnClickListener(v -> row.rowSwitch.toggle());
        parent.addView(row.getRoot());
    }

    // ---------- actions ----------

    private void confirmClearSearch() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear search history?")
                .setMessage("Removes your recent searches and recently searched anime.")
                .setPositiveButton("Clear", (d, w) -> {
                    requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
                            .edit().remove("recent").remove("recent_anime").apply();
                    toast("Search history cleared");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearWatch() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear watch history?")
                .setMessage("This also empties Continue Watching. Favorites are kept.")
                .setPositiveButton("Clear", (d, w) -> {
                    LibraryStore.clearHistory(requireContext());
                    toast("Watch history cleared");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String versionName() {
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void toast(String text) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
