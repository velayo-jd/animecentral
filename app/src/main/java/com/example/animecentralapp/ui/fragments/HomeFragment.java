package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentHomeBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.ui.adapters.ContinueAdapter;
import com.example.animecentralapp.ui.adapters.HeroAdapter;
import com.example.animecentralapp.util.FirebaseUtil;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.util.Motion;
import com.example.animecentralapp.util.ShimmerDrawable;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.animecentralapp.util.LightPagerSnapHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private AnimeViewModel viewModel;
    private List<Anime> allAnime = new ArrayList<>();

    private HeroAdapter heroAdapter;
    private ContinueAdapter continueAdapter;
    private LightPagerSnapHelper heroSnap;
    private int heroCount = 0;
    private int heroPosition = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoScroll = new Runnable() {
        @Override
        public void run() {
            if (binding == null || heroCount < 2) return;
            if (binding.heroPager.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                handler.postDelayed(this, 1000);       // busy: try again shortly
                return;
            }
            binding.heroPager.smoothScrollToPosition(heroPosition + 1);
            handler.postDelayed(this, AUTO_SCROLL_MS);
        }
    };

    /** Number of genre rows shown on Home. */
    private static final int MAX_SECTIONS = 5;
    private static final int MAX_PER_SECTION = 10;
    /** Pages in the featured banner, and how long each stays. */
    private static final int MAX_HERO = 5;
    private static final long AUTO_SCROLL_MS = 5000;
    /** Cards in the "Continue watching" row. */
    private static final int MAX_CONTINUE = 6;

    /**
     * Created once per app process, so the genre rows are reshuffled on every fresh app open
     * but stay the same while switching between tabs.
     */
    private static final long SESSION_SEED = System.nanoTime();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        applyShimmer(binding.skeleton.getRoot());
        setupTopBar();
        binding.homeRefresh.setColorSchemeColors(requireContext().getColor(R.color.ac_accent));
        binding.homeRefresh.setProgressBackgroundColorSchemeColor(requireContext().getColor(R.color.ac_row_bg));
        binding.homeRefresh.setOnRefreshListener(() -> viewModel.refreshAllAnime());
        setupHero();
        setupContinue();
        observeData();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContinue();   // the user may have just watched something
        startAutoScroll();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(autoScroll);
    }

    // ---------- setup ----------

    private void setupTopBar() {
        // Top bar: hamburger opens the side menu, search icon opens Search
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));
    }

    /** Gives every skeleton box (tagged with its corner radius in dp) a shimmering background. */
    private void applyShimmer(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) applyShimmer(group.getChildAt(i));
        } else if (v.getTag() instanceof String) {
            try {
                float radius = Float.parseFloat((String) v.getTag()) * dp(1);
                v.setBackground(new ShimmerDrawable(requireContext(), radius));
            } catch (NumberFormatException ignored) { }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void setupHero() {
        heroAdapter = new HeroAdapter(new HeroAdapter.Listener() {
            @Override
            public void onWatch(Anime anime) {
                openWatch(anime);
            }

            @Override
            public void onDetails(Anime anime, View imageView) {
                Motion.openDetails(HomeFragment.this, anime, imageView);
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.heroPager.setLayoutManager(lm);
        binding.heroPager.setAdapter(heroAdapter);
        final int touchSlop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();

        heroSnap = new LightPagerSnapHelper();
        heroSnap.attachToRecyclerView(binding.heroPager);

        binding.heroPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    heroSnap.resetDrag();
                    handler.removeCallbacks(autoScroll); // the user is swiping: pause the slideshow
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapped = heroSnap.nearestView(lm);
                    if (snapped != null) {
                        heroPosition = lm.getPosition(snapped);
                        updateDots();
                    }
                    startAutoScroll();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (rv.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) heroSnap.addDrag(dx);
                View snapped = heroSnap.nearestView(lm);
                if (snapped == null) return;
                int pos = lm.getPosition(snapped);
                if (pos != heroPosition) {
                    heroPosition = pos;
                    updateDots();
                }
            }
        });
    }

    private void setupContinue() {
        continueAdapter = new ContinueAdapter(item -> {
            Bundle args = new Bundle();
            args.putString("animeId", item.anime.getId());
            if (item.episodeId != null) args.putString("episodeId", item.episodeId);
            NavHostFragment.findNavController(this).navigate(R.id.watch, args);
        });
        binding.continueRecycler.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.continueRecycler.setAdapter(continueAdapter);
    }

    private void observeData() {
        viewModel.getAllAnime().observe(getViewLifecycleOwner(), animeList -> {
            binding.homeRefresh.setRefreshing(false);
            allAnime = animeList == null ? new ArrayList<>() : animeList;

            // Data is here: swap the grey boxes for the real content
            binding.skeleton.getRoot().setVisibility(View.GONE);
            binding.homeContent.setVisibility(View.VISIBLE);

            updateHero(allAnime);
            refreshContinue();
            buildGenreSections(allAnime);
        });
    }

    // ---------- featured banner ----------

    private static boolean hasImage(Anime a) {
        return notEmpty(a.getThumbnailUrl()) || notEmpty(a.getPosterUrl());
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Recommended anime first, then ones with a wide picture, then anything with an image. */
    private List<Anime> pickFeatured(List<Anime> all) {
        List<Anime> out = new ArrayList<>();
        for (Anime a : all) {
            if (out.size() >= MAX_HERO) break;
            if (a.isRecommended() && hasImage(a)) out.add(a);
        }
        for (Anime a : all) {
            if (out.size() >= MAX_HERO) break;
            if (!out.contains(a) && notEmpty(a.getThumbnailUrl())) out.add(a);
        }
        for (Anime a : all) {
            if (out.size() >= MAX_HERO) break;
            if (!out.contains(a) && hasImage(a)) out.add(a);
        }
        return out;
    }

    private void updateHero(List<Anime> all) {
        List<Anime> featured = pickFeatured(all);
        heroCount = featured.size();
        heroAdapter.submit(featured);
        // Start in the middle (on a multiple of the count) so both directions have room to swipe
        if (heroCount > 1) {
            heroPosition = (Integer.MAX_VALUE / 2) - ((Integer.MAX_VALUE / 2) % heroCount);
            binding.heroPager.scrollToPosition(heroPosition);
        } else {
            heroPosition = 0;
            binding.heroPager.scrollToPosition(0);
        }
        binding.heroPager.setVisibility(featured.isEmpty() ? View.GONE : View.VISIBLE);
        buildDots();
        startAutoScroll();
    }

    private void buildDots() {
        LinearLayout dots = binding.heroDots;
        dots.removeAllViews();
        if (heroCount < 2) {
            dots.setVisibility(View.GONE);
            return;
        }
        dots.setVisibility(View.VISIBLE);
        for (int i = 0; i < heroCount; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(6), (int) dp(6));
            lp.setMargins((int) dp(3), 0, (int) dp(3), 0);
            dot.setLayoutParams(lp);
            dots.addView(dot);
        }
        updateDots();
    }

    /** The current page gets a longer, brighter dot. */
    /** The current page gets a longer, brighter dot; changes glide instead of snapping. */
    private void updateDots() {
        if (binding == null) return;
        LinearLayout dots = binding.heroDots;
        int active = heroCount > 0 ? heroPosition % heroCount : 0;

        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean on = i == active;
            Boolean was = (Boolean) dot.getTag();
            if (was != null && was == on) continue;          // nothing changed for this dot
            dot.setTag(on);

            dot.setBackgroundResource(on ? R.drawable.ac_dot_on : R.drawable.ac_dot_off);
            int from = dot.getLayoutParams().width;
            int to = (int) dp(on ? 18 : 6);

            if (was == null) {                               // first time: no animation
                setDotWidth(dot, to);
                continue;
            }

            ValueAnimator anim = ValueAnimator.ofInt(from, to);
            anim.setDuration(on ? 320 : 220);
            anim.setInterpolator(on ? new OvershootInterpolator(2.2f)
                    : new DecelerateInterpolator());
            anim.addUpdateListener(a -> setDotWidth(dot, (int) a.getAnimatedValue()));
            anim.start();

            if (on) {                                         // a little pop of brightness
                dot.setAlpha(0.45f);
                dot.animate().alpha(1f).setDuration(320).start();
            }
        }
    }

    private void setDotWidth(View dot, int width) {
        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        lp.width = Math.max(width, (int) dp(4));    // overshoot never makes it vanish
        dot.setLayoutParams(lp);
    }

    private void startAutoScroll() {
        handler.removeCallbacks(autoScroll);
        if (binding != null && heroCount > 1) handler.postDelayed(autoScroll, AUTO_SCROLL_MS);
    }

    // ---------- continue watching ----------

    private void refreshContinue() {
        if (binding == null) return;
        List<ContinueAdapter.Item> items = new ArrayList<>();
        for (LibraryStore.WatchEntry e : LibraryStore.getHistory(requireContext())) {
            if (e.finished) continue;
            Anime anime = findAnime(e.animeId);
            if (anime == null) continue;
            items.add(new ContinueAdapter.Item(anime, e.episodeId));
            if (items.size() >= MAX_CONTINUE) break;
        }
        binding.continueSection.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        continueAdapter.submit(items);
        for (ContinueAdapter.Item item : items) loadProgress(item);
    }

    @Nullable
    private Anime findAnime(String id) {
        for (Anime a : allAnime) {
            if (a.getId() != null && a.getId().equals(id)) return a;
        }
        return null;
    }

    /** Looks up which episode the user is on, to fill in "Episode 3 of 12" and the bar. */
    private void loadProgress(ContinueAdapter.Item item) {
        FirebaseUtil.getFirestore().collection("anime").document(item.anime.getId())
                .collection("episodes").orderBy("number").get()
                .addOnSuccessListener(snapshot -> {
                    if (binding == null) return;
                    int index = 0;
                    int number = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        boolean match = item.episodeId == null ? index == 0
                                : doc.getId().equals(item.episodeId);
                        if (match) {
                            Long n = doc.getLong("number");
                            number = n != null ? n.intValue() : index + 1;
                            break;
                        }
                        index++;
                    }
                    item.episodeNumber = number;
                    item.total = item.anime.getEpisodes() > 0
                            ? item.anime.getEpisodes() : snapshot.size();
                    continueAdapter.notifyDataSetChanged();
                });
    }

    // ---------- genre rows ----------

    private void buildGenreSections(List<Anime> all) {
        binding.genreSections.removeAllViews();
        if (all == null || all.isEmpty()) return;

        // Random but stable for this app session
        List<String> genres = TagUtils.collectGenres(all);
        Collections.shuffle(genres, new Random(SESSION_SEED));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int shown = 0;
        for (String genre : genres) {
            if (shown >= MAX_SECTIONS) break;

            List<Anime> matches = TagUtils.filterByTag(all, genre);
            if (matches.isEmpty()) continue;
            Collections.shuffle(matches, new Random(SESSION_SEED + genre.hashCode()));
            if (matches.size() > MAX_PER_SECTION) {
                matches = new ArrayList<>(matches.subList(0, MAX_PER_SECTION));
            }

            View section = inflater.inflate(R.layout.item_home_section,
                    binding.genreSections, false);
            ((TextView) section.findViewById(R.id.section_title)).setText(genre);
            section.findViewById(R.id.section_see_all).setOnClickListener(v -> openTag(genre));

            AnimeAdapter rowAdapter = new AnimeAdapter(this::openDetails, 190, 130);
            // The poster glides into the Details page
            rowAdapter.setViewClickListener((anime, poster) ->
                    Motion.openDetails(this, anime, poster));
            RecyclerView row = section.findViewById(R.id.section_recycler);
            row.setLayoutManager(new LinearLayoutManager(
                    requireContext(), LinearLayoutManager.HORIZONTAL, false));
            row.setAdapter(rowAdapter);
            rowAdapter.submitList(matches);

            binding.genreSections.addView(section);
            shown++;
        }
    }

    // ---------- navigation ----------

    private void openTag(String tag) {
        Bundle args = new Bundle();
        args.putString("tag", tag);
        NavHostFragment.findNavController(this).navigate(R.id.tag_browse, args);
    }

    private void openDetails(Anime anime) {
        Motion.openDetails(this, anime, null);
    }

    private void openWatch(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.watch, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(autoScroll);
        binding = null;
    }
}
