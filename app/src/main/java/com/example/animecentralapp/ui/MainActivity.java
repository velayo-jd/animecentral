package com.example.animecentralapp.ui;

import android.os.Bundle;
import android.view.Window;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ActivityMainBinding;
import com.example.animecentralapp.databinding.ItemDrawerDropdownBinding;
import com.example.animecentralapp.databinding.ItemDrawerLinkBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.AppSettings;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;
    private List<Anime> allAnime = new ArrayList<>(); // used by "Random anime"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Light / dark choice from Settings (dark by default)
        boolean light = AppSettings.isLight(this);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (light) {
            // White status / navigation bars with dark icons
            Window w = getWindow();
            w.setStatusBarColor(ContextCompat.getColor(this, R.color.ac_bg));
            w.setNavigationBarColor(ContextCompat.getColor(this, R.color.ac_bg));
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
            c.setAppearanceLightStatusBars(true);
            c.setAppearanceLightNavigationBars(true);
        }

        setupNavigation();
        setupDrawer();

        // First launch: show the log in screen (guests can skip it)
        if (savedInstanceState == null && navController != null
                && FirebaseAuth.getInstance().getCurrentUser() == null
                && !AppSettings.hasSeenLogin(this)) {
            navController.navigate(R.id.login, null,
                    new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
        }

        new ViewModelProvider(this).get(AnimeViewModel.class).getAllAnime().observe(this, list -> {
            if (list != null) allAnime = list;
        });
    }

    /** Called by the hamburger icon in each page's top bar. */
    public void openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;

        navController = navHostFragment.getNavController();

        // Tapping a tab always opens that tab fresh (anything opened inside the old tab is closed)
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, false)
                    .build();
            try {
                navController.navigate(item.getItemId(), null, options);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });

        // Tapping the tab you're already on goes back to that tab's main screen
        binding.bottomNavigation.setOnItemReselectedListener(item ->
                navController.popBackStack(item.getItemId(), false));

        // Keep the highlighted tab in sync when screens change, and close the side menu
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            MenuItem item = binding.bottomNavigation.getMenu().findItem(destination.getId());
            if (item != null) item.setChecked(true);

            // Log in / sign up screens have no bottom bar and no side menu
            boolean authScreen = destination.getId() == R.id.login
                    || destination.getId() == R.id.signup;
            binding.bottomNavigation.setVisibility(authScreen ? View.GONE : View.VISIBLE);
            binding.bottomDivider.setVisibility(authScreen ? View.GONE : View.VISIBLE);
            binding.drawerLayout.setDrawerLockMode(authScreen
                    ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);

            // Remember what the user opens in the player (feeds Watch History / Continue Watching)
            if (destination.getId() == R.id.watch && args != null
                    && args.getString("animeId") != null) {
                LibraryStore.recordWatch(this, args.getString("animeId"),
                        args.getString("episodeId"));
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        });
    }

    private void setupDrawer() {
        // Back button closes the side menu first
        OnBackPressedCallback closeDrawer = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawer);
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                refreshDrawerName();
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                closeDrawer.setEnabled(false);
            }
        });

        addSection("Browse", R.drawable.ic_drawer_browse,
                new String[]{"Genres", "Types", "Recommended", "Random anime"},
                new Runnable[]{
                        () -> goTo(R.id.all_tags, bundle("kind", "genre")),
                        () -> goTo(R.id.all_tags, bundle("kind", "type")),
                        () -> goToTab(R.id.videos),
                        this::openRandomAnime});

        addSection("My Library", R.drawable.ic_drawer_library,
                new String[]{"Watch History", "Continue Watching", "Favorites", "Recent Searches"},
                new Runnable[]{
                        () -> goToTab(R.id.history),
                        () -> goTo(R.id.collection, bundle("mode", "continue")),
                        () -> goTo(R.id.collection, bundle("mode", "favorites")),
                        () -> goTo(R.id.search, null)});

        addSection("Discover", R.drawable.ic_drawer_discover,
                new String[]{"Airing Now", "Completed", "Movies", "Recently Searched"},
                new Runnable[]{
                        () -> goTo(R.id.collection, bundle("mode", "airing")),
                        () -> goTo(R.id.collection, bundle("mode", "completed")),
                        () -> goTo(R.id.tag_browse, bundle("tag", "Movie")),
                        () -> goTo(R.id.search, null)});

        addSection("Account", R.drawable.ic_drawer_account,
                new String[]{"Profile", "Settings", "About"},
                new Runnable[]{
                        () -> goTo(R.id.profile, null),
                        () -> goToTab(R.id.settings),
                        this::showAbout});

        // Footer: name + profile, settings
        refreshDrawerName();
        binding.drawerProfile.setOnClickListener(v -> navController.navigate(R.id.profile));
        binding.drawerSettings.setOnClickListener(v -> navController.navigate(R.id.settings));
    }

    // ---------- side menu helpers ----------

    /** Name shown in the side menu footer: the name set on Profile, else the account, else Guest. */
    private void refreshDrawerName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        binding.drawerName.setText(LibraryStore.displayName(
                this, user != null ? user.getDisplayName() : null));
    }

    private void addSection(String title, int iconRes, String[] labels, Runnable[] actions) {
        ItemDrawerDropdownBinding row = ItemDrawerDropdownBinding.inflate(
                getLayoutInflater(), binding.drawerItems, false);
        row.dropdownTitle.setText(title);
        row.dropdownIcon.setImageResource(iconRes);
        row.dropdownHeader.setOnClickListener(v -> {
            boolean open = row.dropdownBody.getVisibility() == View.VISIBLE;
            row.dropdownBody.setVisibility(open ? View.GONE : View.VISIBLE);
            row.dropdownArrow.setText(open ? "▾" : "▴");
        });

        for (int i = 0; i < labels.length; i++) {
            ItemDrawerLinkBinding link = ItemDrawerLinkBinding.inflate(
                    getLayoutInflater(), row.dropdownBody, false);
            link.linkText.setText(labels[i]);
            Runnable action = actions[i];
            link.getRoot().setOnClickListener(v -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                action.run();
            });
            row.dropdownBody.addView(link.getRoot());
        }
        binding.drawerItems.addView(row.getRoot());
    }

    private static Bundle bundle(String key, String value) {
        Bundle b = new Bundle();
        b.putString(key, value);
        return b;
    }

    private void goTo(int destination, Bundle args) {
        navController.navigate(destination, args);
    }

    /** Opens a bottom-bar page fresh, same as tapping its tab. */
    private void goToTab(int destination) {
        binding.bottomNavigation.setSelectedItemId(destination);
    }

    private void openRandomAnime() {
        if (allAnime.isEmpty()) {
            Toast.makeText(this, "No anime loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Anime pick = allAnime.get(new Random().nextInt(allAnime.size()));
        goTo(R.id.details, bundle("animeId", pick.getId()));
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Anime Central")
                .setMessage("A school project: an Android anime streaming app built with Java, "
                        + "Firebase and Vimeo / Rumble players.")
                .setPositiveButton("OK", null)
                .show();
    }
}
