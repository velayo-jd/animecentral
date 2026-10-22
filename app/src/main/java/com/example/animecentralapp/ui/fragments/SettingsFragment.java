package com.example.animecentralapp.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.animecentralapp.util.AvatarLoader;
import android.view.View;

import com.example.animecentralapp.util.UpdateChecker;
import com.example.animecentralapp.util.UpdateDialog;

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
    private CompoundButton lightSwitch, autoplaySwitch;

    // Keeps the switches in step when a setting is changed elsewhere (e.g. the side menu)
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (p, key) -> syncSwitches();

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
        lightSwitch = addSwitchRow(binding.sectionOneRows, "Light mode",
                AppSettings.isLight(requireContext()), on -> {
                    if (on == AppSettings.isLight(requireContext())) return;
                    AppSettings.setLight(requireContext(), on);
                    AppCompatDelegate.setDefaultNightMode(on
                            ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
                });
        autoplaySwitch = addSwitchRow(binding.sectionOneRows, "Autoplay next episode",
                AppSettings.isAutoplay(requireContext()),
                on -> AppSettings.setAutoplay(requireContext(), on));
        addRow(binding.sectionOneRows, "Clear Search History", "", this::confirmClearSearch);
        addRow(binding.sectionOneRows, "Clear Watch History", "", this::confirmClearWatch);

        String pending = UpdateChecker.pendingVersion(requireContext());
        addRow(binding.sectionTwoRows, "Check for updates",
                pending != null ? "v" + pending + " available" : "›", this::checkForUpdates);
        // Section 2: About
        addRow(binding.sectionTwoRows, "Report a bug", "›", () ->
                NavHostFragment.findNavController(this).navigate(R.id.bug_reports));
        addRow(binding.sectionTwoRows, "Version", versionName(), null);
        addRow(binding.sectionTwoRows, "Credits", "›", () -> showInfo(
                "Made By:",
                "Velayo, John Daniel\n"
                        + "Sales, Eman\n"
                        + "Te, Jozhuart Ivan\n"
                        + "Zamora, Jeric\n"
                        +"Vicenio, Rg"));
        addRow(binding.sectionTwoRows, "About Anime Central", "›", () -> showInfo(
                "Anime Central",
                "An Android anime streaming app built with Java, "
                        + "Firebase, and Vimeo/Rumble/YT players."));
        addRow(binding.sectionTwoRows, "Video Sources", "›", () -> showInfo(
                "Video Sources",
                "Videos are played from Vimeo, Rumble, and YouTube. They are streamed from those "
                        + "services and are not stored in this app."));
    }

    @Override
    public void onStart() {
        super.onStart();
        prefs().registerOnSharedPreferenceChangeListener(prefListener);
        syncSwitches();
    }

    @Override
    public void onStop() {
        super.onStop();
        prefs().unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBanner(); // the name may have been changed on the Profile page
    }

    private SharedPreferences prefs() {
        // Same file AppSettings uses
        return requireContext().getApplicationContext()
                .getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    /** Makes the two switches match the saved settings. */
    private void syncSwitches() {
        if (binding == null || lightSwitch == null || autoplaySwitch == null) return;
        boolean light = AppSettings.isLight(requireContext());
        boolean auto = AppSettings.isAutoplay(requireContext());
        if (lightSwitch.isChecked() != light) lightSwitch.setChecked(light);
        if (autoplaySwitch.isChecked() != auto) autoplaySwitch.setChecked(auto);
    }

    private void checkForUpdates() {
        toast("Checking for updates…");
        UpdateChecker.check(requireContext(), (info, error) -> {
            if (!isAdded()) return;
            if (error != null) toast(error);
            else if (info == null) toast("You're on the latest version");
            else UpdateDialog.show(requireActivity(), info);
        });
    }

    private void refreshBanner() {
        if (binding == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = LibraryStore.displayName(requireContext(),
                user != null ? user.getDisplayName() : null);
        binding.bannerName.setText(name);
        binding.bannerAvatar.setText(name.substring(0, 1).toUpperCase());
        boolean hasPhoto = AvatarLoader.load(binding.bannerPhoto, user);
        binding.bannerPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
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

    private CompoundButton addSwitchRow(LinearLayout parent, String label, boolean checked,
                                        SwitchListener listener) {
        ItemSettingsRowBinding row = ItemSettingsRowBinding.inflate(
                getLayoutInflater(), parent, false);
        row.rowLabel.setText(label);
        row.rowValue.setVisibility(View.GONE);
        row.rowSwitch.setVisibility(View.VISIBLE);
        row.rowSwitch.setSaveEnabled(false);   // <-- this line
        row.rowSwitch.setChecked(checked);
        row.rowSwitch.setOnCheckedChangeListener((b, on) -> listener.onChanged(on));
        row.getRoot().setOnClickListener(v -> row.rowSwitch.toggle());
        parent.addView(row.getRoot());
        return row.rowSwitch;
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
        lightSwitch = null;
        autoplaySwitch = null;
    }
}