package com.example.animecentralapp.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.animecentralapp.util.AvatarLoader;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.ActivityMainBinding;
import com.example.animecentralapp.databinding.ItemDrawerDropdownBinding;
import com.example.animecentralapp.databinding.ItemDrawerLinkBinding;
import com.example.animecentralapp.databinding.ItemDrawerQuickBinding;
import com.example.animecentralapp.databinding.ItemDrawerResumeBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.util.AdminAccess;
import com.example.animecentralapp.util.AppSettings;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.animecentralapp.util.UpdateChecker;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.example.animecentralapp.util.CalmDialog;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;
    private List<Anime> allAnime = new ArrayList<>(); // used by "Random anime" and the resume card
    private int logoTaps = 0;
    private long lastLogoTap = 0;
    private FrameLayout resumeHolder;  // "Continue watching" card
    private LinearLayout adminHolder;  // admin-only section

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Light / dark choice from Settings (dark by default)
        boolean light = AppSettings.isLight(this);
        AppCompatDelegate.setDefaultNightMode(light
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);   // <-- delete these 2 lines

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupBrandTaps();

        Window w = getWindow();
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        if (light) {
            // White status / navigation bars with dark icons
            w.setStatusBarColor(ContextCompat.getColor(this, R.color.ac_bg));
            w.setNavigationBarColor(ContextCompat.getColor(this, R.color.ac_bg));
            c.setAppearanceLightStatusBars(true);
            c.setAppearanceLightNavigationBars(true);
        } else {
            // Back to light icons when switching from light to dark
            c.setAppearanceLightStatusBars(false);
            c.setAppearanceLightNavigationBars(false);
        }

        setupNavigation();
        setupDrawer();
        if (savedInstanceState == null) UpdateChecker.autoCheck(this);

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

    /** Makes the logo + name in every page's top bar tappable. */
    private void setupBrandTaps() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) return;
        FragmentManager fm = host.getChildFragmentManager();
        fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                              @NonNull View v, @Nullable Bundle savedInstanceState) {
                View brand = v.findViewById(R.id.top_bar_brand);
                if (brand != null) brand.setOnClickListener(x -> onBrandTapped());
            }
        }, false);
        // Pages whose view already exists
        for (Fragment f : fm.getFragments()) {
            View v = f.getView();
            View brand = v == null ? null : v.findViewById(R.id.top_bar_brand);
            if (brand != null) brand.setOnClickListener(x -> onBrandTapped());
        }
    }

    private void onBrandTapped() {
        if (navController == null || navController.getCurrentDestination() == null) return;
        int dest = navController.getCurrentDestination().getId();
        if (dest == R.id.login || dest == R.id.signup) return;

        if (dest != R.id.home) {          // anywhere else: go Home
            logoTaps = 0;
            goToTab(R.id.home);
            return;
        }
        // Already on Home: a quick run of taps opens the easter egg
        long now = SystemClock.uptimeMillis();
        logoTaps = (now - lastLogoTap > 700) ? 1 : logoTaps + 1;
        lastLogoTap = now;
        if (logoTaps >= 7) {
            logoTaps = 0;
            CalmDialog.show(this);
        }
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
                refreshResume();
                refreshAdmin();
                refreshToggles();
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                closeDrawer.setEnabled(false);
            }
        });
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        host.getChildFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                                      @NonNull View v, @Nullable Bundle s) {
                        v.setBackgroundColor(Color.TRANSPARENT);   // let the glow show through the page
                        binding.moonGlow.anchorTo(v.findViewById(R.id.top_bar_logo)); // null hides it
                    }
                }, false);

        resumeHolder = new FrameLayout(this);
        binding.drawerItems.addView(resumeHolder);
        addQuickActions();

        addSection(binding.drawerItems, "Browse", R.drawable.ic_drawer_browse,
                new String[]{"Search", "Genres", "Format", "Recommended"},
                new Runnable[]{
                        () -> goTo(R.id.search, null),
                        () -> goTo(R.id.all_tags, bundle("kind", "genre")),
                        () -> goTo(R.id.all_tags, bundle("kind", "type")),
                        () -> goToTab(R.id.videos)});

        addSection(binding.drawerItems, "My Library", R.drawable.ic_drawer_library,
                new String[]{"Watch History", "Continue Watching", "Favorites", "Saved Anime"},
                new Runnable[]{
                        () -> goToTab(R.id.history),
                        () -> goTo(R.id.collection, bundle("mode", "continue")),
                        () -> goTo(R.id.collection, bundle("mode", "favorites")),
                        () -> goTo(R.id.collection, bundle("mode", "saved"))});

        addSection(binding.drawerItems, "Discover", R.drawable.ic_drawer_discover,
                new String[]{"Airing Now", "Completed", "Movies"},
                new Runnable[]{
                        () -> goTo(R.id.collection, bundle("mode", "airing")),
                        () -> goTo(R.id.collection, bundle("mode", "completed")),
                        () -> goTo(R.id.tag_browse, bundle("tag", "Movie"))});

        addSection(binding.drawerItems, "Account", R.drawable.ic_drawer_account,
                new String[]{"Profile", "Settings", "About"},
                new Runnable[]{
                        () -> goTo(R.id.profile, null),
                        () -> goToTab(R.id.settings),
                        this::showAbout});

        adminHolder = new LinearLayout(this);
        adminHolder.setOrientation(LinearLayout.VERTICAL);
        binding.drawerItems.addView(adminHolder);

        // Footer: name + profile, light mode, autoplay, settings
        refreshDrawerName();
        refreshToggles();
        binding.drawerProfile.setOnClickListener(v -> navController.navigate(R.id.profile));
        binding.drawerSettings.setOnClickListener(v -> navController.navigate(R.id.settings));
        binding.drawerTheme.setOnClickListener(v -> {
            boolean on = !AppSettings.isLight(this);
            AppSettings.setLight(this, on);
            // Restarts the screens in the new look
            AppCompatDelegate.setDefaultNightMode(on
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
        });
        binding.drawerAutoplay.setOnClickListener(v -> {
            AppSettings.setAutoplay(this, !AppSettings.isAutoplay(this));
            refreshToggles();
        });
    }

    // ---------- side menu helpers ----------

    /** Name shown in the side menu footer: the name set on Profile, else the account, else Guest. */
    private void refreshDrawerName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        binding.drawerName.setText(LibraryStore.displayName(
                this, user != null ? user.getDisplayName() : null));
        AvatarLoader.load(binding.drawerAvatar, user);
    }

    private void refreshToggles() {
        binding.drawerTheme.setText(AppSettings.isLight(this) ? "☀" : "☾");
        binding.drawerAutoplay.setTextColor(ContextCompat.getColor(this,
                AppSettings.isAutoplay(this) ? R.color.ac_accent : R.color.ac_text_faint));
    }

    /** Surprise me / Catalogue / Saved shortcuts above the sections. */
    private void addQuickActions() {
        ItemDrawerQuickBinding q = ItemDrawerQuickBinding.inflate(
                getLayoutInflater(), binding.drawerItems, false);
        q.quickSurprise.setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            openRandomAnime();
        });
        q.quickCatalogue.setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            goTo(R.id.catalog, null);
        });
        q.quickSaved.setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            goToTab(R.id.history);
        });
        binding.drawerItems.addView(q.getRoot());
    }

    /** The "Continue watching" card: the most recent unfinished anime that has an episode. */
    private void refreshResume() {
        resumeHolder.removeAllViews();
        LibraryStore.WatchEntry pick = null;
        for (LibraryStore.WatchEntry e : LibraryStore.getHistory(this)) {
            if (e.episodeId != null && !e.finished) { pick = e; break; }
        }
        if (pick == null) return;
        Anime anime = null;
        for (Anime a : allAnime) if (a.getId().equals(pick.animeId)) { anime = a; break; }
        if (anime == null) return;

        final String animeId = pick.animeId, episodeId = pick.episodeId;
        ItemDrawerResumeBinding row = ItemDrawerResumeBinding.inflate(
                getLayoutInflater(), resumeHolder, false);
        row.resumeTitle.setText(anime.getTitle());
        row.resumeSub.setText("Pick up where you left off");
        row.getRoot().setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            Bundle b = bundle("animeId", animeId);
            b.putString("episodeId", episodeId);
            goTo(R.id.watch, b);
        });
        resumeHolder.addView(row.getRoot());

        // Fill in the episode number once it loads
        FirebaseFirestore.getInstance().collection("anime").document(animeId)
                .collection("episodes").document(episodeId).get()
                .addOnSuccessListener(d -> {
                    Object n = d.get("number");
                    if (n instanceof Number && row.getRoot().isAttachedToWindow()) {
                        row.resumeSub.setText("Episode " + ((Number) n).intValue());
                    }
                });
    }

    /** Admin-only section; rebuilt on every open so logging in/out is picked up. */
    private void refreshAdmin() {
        adminHolder.removeAllViews();
        if (!AdminAccess.isAdmin()) return;
        addSection(adminHolder, "Admin", R.drawable.ic_drawer_account,
                new String[]{"Import from AniList"},
                new Runnable[]{() -> goTo(R.id.import_anilist, null)});
    }

    private void addSection(ViewGroup parent, String title, int iconRes,
                            String[] labels, Runnable[] actions) {
        ItemDrawerDropdownBinding row = ItemDrawerDropdownBinding.inflate(
                getLayoutInflater(), parent, false);
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
        parent.addView(row.getRoot());
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