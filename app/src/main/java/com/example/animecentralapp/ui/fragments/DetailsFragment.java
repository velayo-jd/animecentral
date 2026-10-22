package com.example.animecentralapp.ui.fragments;

import android.graphics.LinearGradient;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import com.example.animecentralapp.model.Discussion;
import com.example.animecentralapp.repository.DiscussionRepository;
import com.example.animecentralapp.ui.adapters.DiscussionAdapter;
import com.example.animecentralapp.util.NewDiscussionSheet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.ChangeBounds;
import androidx.transition.ChangeImageTransform;
import androidx.transition.ChangeTransform;
import androidx.transition.TransitionSet;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentDetailsBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.model.Episode;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.EpisodeAdapter;
import com.example.animecentralapp.util.AdminAccess;
import com.example.animecentralapp.util.EpisodeStore;
import com.example.animecentralapp.util.EpisodeSwipeCallback;
import com.example.animecentralapp.util.LibraryStore;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.example.animecentralapp.viewmodel.EpisodeViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Anime details screen: blurred-poster header, Watch/Resume button, collapsible synopsis,
 * genre chips and the episode list.
 */
public class DetailsFragment extends Fragment {
    private FragmentDetailsBinding binding;
    private String animeId;
    private List<Episode> episodes = new ArrayList<>();
    private EpisodeAdapter episodeAdapter;
    private boolean synopsisExpanded = false;
    private boolean newestFirst = false;   // list display only; "episodes" stays in normal order
    private boolean discussionTab = false;
    private DiscussionAdapter discussionAdapter;
    private ListenerRegistration discussionReg;
    private boolean transitionStarted = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Poster flies from the list into the header (and back)
        setSharedElementEnterTransition(posterMove());
        setSharedElementReturnTransition(posterMove());
        postponeEnterTransition();
    }

    private TransitionSet posterMove() {
        return (TransitionSet) new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new ChangeTransform())
                .addTransition(new ChangeImageTransform())
                .setDuration(350);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Safety net: never leave the transition postponed if the poster is slow
        view.postDelayed(this::startTransitionOnce, 500);

        Bundle args = getArguments();
        animeId = args != null ? args.getString("animeId") : null;
        if (animeId == null) {
            startTransitionOnce();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        updateHeart();
        binding.favoriteBtn.setOnClickListener(v -> {
            boolean nowFavorite = LibraryStore.toggleFavorite(requireContext(), animeId);
            updateHeart();
            bounce(binding.favoriteBtn);
            Toast.makeText(requireContext(),
                    nowFavorite ? "Added to Favorites" : "Removed from Favorites",
                    Toast.LENGTH_SHORT).show();
        });

        updateSaved();
        binding.savedBtn.setOnClickListener(v -> {
            boolean now = LibraryStore.toggleSaved(requireContext(), animeId);
            updateSaved();
            bounce(binding.savedBtn);
            Toast.makeText(requireContext(),
                    now ? "Saved anime" : "Removed from Saved",
                    Toast.LENGTH_SHORT).show();
        });

        binding.synopsisToggle.setOnClickListener(v -> toggleSynopsis());

        AnimeViewModel animeViewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        EpisodeViewModel episodeViewModel = new ViewModelProvider(this).get(EpisodeViewModel.class);

        episodeAdapter = new EpisodeAdapter((episode, position) -> openWatch(episode.getId()));
        // Admins: "+" adds an episode, long-press a row to edit or delete it
        if (AdminAccess.isAdmin()) {
            binding.addEpisodeBtn.setVisibility(View.VISIBLE);
            binding.addEpisodeBtn.setOnClickListener(v -> openEpisodeForm(null));
            episodeAdapter.setLongClickListener((episode, position) ->
                    openEpisodeForm(episode.getId()));
        }
        binding.episodesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.episodesRecyclerView.setAdapter(episodeAdapter);
        binding.episodesRecyclerView.setNestedScrollingEnabled(false);
        binding.sortEpisodesBtn.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            showEpisodes();
            Toast.makeText(requireContext(),
                    newestFirst ? "Newest first" : "Oldest first", Toast.LENGTH_SHORT).show();
        });
        refreshStates();
        setupDiscussion();

        // Swipe right = watched on/off, swipe left = bookmark on/off
        new ItemTouchHelper(new EpisodeSwipeCallback(requireContext(),
                new EpisodeSwipeCallback.Host() {
                    @Override public boolean isWatchedAt(int p) {
                        return episodeAdapter.isWatched(episodeAdapter.episodeAt(p));
                    }
                    @Override public boolean isBookmarkedAt(int p) {
                        return episodeAdapter.isBookmarked(episodeAdapter.episodeAt(p));
                    }
                    @Override public void onSwipeRight(int p) {
                        EpisodeStore.toggleWatched(requireContext(), animeId,
                                episodeAdapter.episodeAt(p).getId());
                        refreshStates();
                    }
                    @Override public void onSwipeLeft(int p) {
                        EpisodeStore.toggleBookmark(requireContext(), animeId,
                                episodeAdapter.episodeAt(p).getId());
                        refreshStates();
                    }
                })).attachToRecyclerView(binding.episodesRecyclerView);

        animeViewModel.getAnimeById(animeId).observe(getViewLifecycleOwner(), this::showAnime);
        binding.detailsRefresh.setColorSchemeColors(requireContext().getColor(R.color.ac_accent));
        binding.detailsRefresh.setProgressBackgroundColorSchemeColor(requireContext().getColor(R.color.ac_row_bg));
        binding.detailsRefresh.setOnRefreshListener(() -> {
            final boolean[] done = {false};
            Runnable stop = () -> {
                if (done[0] || binding == null) return;
                done[0] = true;
                binding.detailsRefresh.setRefreshing(false);
            };

            animeViewModel.getAnimeById(animeId).observe(getViewLifecycleOwner(), a -> {
                if (a != null) showAnime(a);       // a failed fetch keeps what's on screen
                refreshStates();
                stop.run();
            });

            // Safety net: never spin longer than 6 seconds, even if nothing comes back
            binding.detailsRefresh.postDelayed(stop, 6000);
        });

        episodeViewModel.getEpisodes(animeId).observe(getViewLifecycleOwner(), list -> {
            episodes = list == null ? new ArrayList<>() : list;
            showEpisodes();
            applyTab();   // <-- was the noEpisodesText line; applyTab() sets that visibility itself
            binding.episodeCount.setText(episodes.isEmpty() ? "" : episodes.size() + " available");
            updateResume();
        });

        binding.watchNowBtn.setOnClickListener(v -> {
            if (episodes.isEmpty()) {
                Toast.makeText(requireContext(), "No episodes yet", Toast.LENGTH_SHORT).show();
                return;
            }
            Episode target = findCurrentEpisode();
            if (target == null) target = firstUnwatched();
            openWatch(target.getId());
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the player: the "current" episode may have changed
        if (binding != null && animeId != null) updateResume();
    }

    // ---------- Header / info ----------

    private void showAnime(@Nullable Anime anime) {
        if (anime == null || binding == null) return;

        // Title, with the small "Ongoing · 24 eps · 2026" line directly underneath
        List<String> meta = new ArrayList<>();
        String shownStatus = displayStatus(anime.getStatus());
        if (shownStatus != null) meta.add(shownStatus);
        if (anime.getEpisodes() > 0) {
            meta.add(anime.getEpisodes() + (anime.getEpisodes() == 1 ? " episode" : " episodes"));
        }
        String season = anime.getSeason();
        if (season != null && !season.trim().isEmpty()) meta.add(season.trim());
        else if (anime.getYear() > 0) meta.add(String.valueOf(anime.getYear()));
        String title = anime.getTitle() == null ? "" : anime.getTitle().trim();
        SpannableStringBuilder header = new SpannableStringBuilder(title);
        if (!meta.isEmpty()) {
            header.append("\n");
            int start = header.length();
            header.append(android.text.TextUtils.join("  ·  ", meta));
            header.setSpan(new RelativeSizeSpan(0.6f), start, header.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            header.setSpan(new StyleSpan(Typeface.NORMAL), start, header.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            header.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.ac_text_dim)),
                    start, header.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.animeTitle.setText(header);

        showSynopsis(anime.getSynopsis());
        showGenres(anime.getGenres());

        String posterUrl = anime.getPosterUrl();
        if (posterUrl == null || posterUrl.trim().isEmpty()) {
            startTransitionOnce();
            return;
        }
        posterUrl = posterUrl.trim();

        // Backdrop: the same poster loaded tiny and stretched = cheap blur
        Glide.with(this).load(posterUrl).override(24, 36).into(binding.animeBackdrop);

        Glide.with(this).load(posterUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        startTransitionOnce();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        startTransitionOnce();
                        return false;
                    }
                })
                .into(binding.animePoster);
    }

    private void setupDiscussion() {
        discussionAdapter = new DiscussionAdapter(thread -> {
            Bundle b = new Bundle();
            b.putString("animeId", animeId);
            b.putString("threadId", thread.getId());
            NavHostFragment.findNavController(this).navigate(R.id.discussion, b);
        });
        binding.threadsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.threadsRecyclerView.setAdapter(discussionAdapter);
        binding.threadsRecyclerView.setNestedScrollingEnabled(false);

        binding.tabEpisodes.setOnClickListener(v -> { discussionTab = false; applyTab(); });
        binding.tabDiscussion.setOnClickListener(v -> { discussionTab = true; applyTab(); });
        binding.newThreadBtn.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(requireContext(), "Sign in to start a discussion",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            NewDiscussionSheet.show(requireContext(), animeId, null);
        });

        discussionReg = new DiscussionRepository().listenThreads(animeId, list -> {
            if (binding == null) return;
            discussionAdapter.submitList(list, this::applyTab);
        });
        applyTab();
    }

    /** Shows the Episodes or the Discussion content, depending on the selected tab. */
    private void applyTab() {
        if (binding == null) return;
        boolean d = discussionTab;

        binding.tabEpisodes.setTextColor(ContextCompat.getColor(requireContext(),
                d ? R.color.ac_text_dim : R.color.ac_text));
        binding.tabEpisodes.setTypeface(null, d ? Typeface.NORMAL : Typeface.BOLD);
        binding.tabDiscussion.setTextColor(ContextCompat.getColor(requireContext(),
                d ? R.color.ac_text : R.color.ac_text_dim));
        binding.tabDiscussion.setTypeface(null, d ? Typeface.BOLD : Typeface.NORMAL);

        int eps = d ? View.GONE : View.VISIBLE;
        binding.episodeCount.setVisibility(eps);
        binding.sortEpisodesBtn.setVisibility(eps);
        binding.episodesRecyclerView.setVisibility(eps);
        binding.addEpisodeBtn.setVisibility(!d && AdminAccess.isAdmin() ? View.VISIBLE : View.GONE);
        binding.noEpisodesText.setVisibility(!d && episodes.isEmpty() ? View.VISIBLE : View.GONE);

        binding.discussionSection.setVisibility(d ? View.VISIBLE : View.GONE);
        binding.noThreadsText.setVisibility(
                d && discussionAdapter != null && discussionAdapter.getItemCount() == 0
                        ? View.VISIBLE : View.GONE);
    }

    private void startTransitionOnce() {
        if (transitionStarted || !isAdded()) return;
        transitionStarted = true;
        startPostponedEnterTransition();
    }

    /** Sends the episodes to the list, in the chosen direction. */
    private void showEpisodes() {
        List<Episode> shown = new ArrayList<>(episodes);
        if (newestFirst) java.util.Collections.reverse(shown);
        episodeAdapter.submitList(shown);
    }

    // ---------- Synopsis (3 lines, last one fades, chevron expands) ----------

    private void showSynopsis(@Nullable String synopsis) {
        final TextView tv = binding.animeSynopsis;
        tv.setText(synopsis == null || synopsis.trim().isEmpty() ? "No synopsis yet." : synopsis);
        synopsisExpanded = false;
        tv.setMaxLines(3);
        tv.setPadding(0, 0, 0, 0);
        tv.getPaint().setShader(null);
        binding.synopsisToggle.setRotation(0f);
        binding.synopsisToggle.setVisibility(View.GONE);
        tv.setOnClickListener(null);
        tv.setClickable(false);
        tv.post(() -> {
            if (binding == null) return;
            Layout layout = tv.getLayout();
            boolean truncated = layout != null && layout.getLineCount() > 0
                    && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            if (!truncated) return;
            fadeLastLine(true);
            binding.synopsisToggle.setVisibility(View.VISIBLE);
            View.OnClickListener toggle = v -> toggleSynopsis();
            binding.synopsisToggle.setOnClickListener(toggle);
            tv.setOnClickListener(toggle);
        });
    }

    /** Fades the bottom (third) line of the collapsed synopsis out, like the reference. */
    private void fadeLastLine(boolean fade) {
        TextView tv = binding.animeSynopsis;
        Layout layout = tv.getLayout();
        if (!fade || layout == null || layout.getLineCount() < 3) {
            tv.getPaint().setShader(null);
        } else {
            float top = layout.getLineTop(2);
            float bottom = layout.getLineBottom(2);
            int c = tv.getCurrentTextColor();
            tv.getPaint().setShader(new LinearGradient(0, top, 0, bottom, c,
                    ColorUtils.setAlphaComponent(c, 30), Shader.TileMode.CLAMP));
        }
        tv.invalidate();
    }

    private void toggleSynopsis() {
        synopsisExpanded = !synopsisExpanded;
        float dp = getResources().getDisplayMetrics().density;
        TransitionManager.beginDelayedTransition((ViewGroup) binding.detailsBody);
        // Expanded: show everything normally and leave room for the chevron under the text
        fadeLastLine(!synopsisExpanded);
        binding.animeSynopsis.setMaxLines(synopsisExpanded ? Integer.MAX_VALUE : 3);
        binding.animeSynopsis.setPadding(0, 0, 0, synopsisExpanded ? (int) (28 * dp) : 0);
        binding.synopsisToggle.animate().rotation(synopsisExpanded ? 180f : 0f).setDuration(200).start();
        binding.synopsisToggle.setContentDescription(
                synopsisExpanded ? "Collapse synopsis" : "Expand synopsis");
        if (!synopsisExpanded) {
            // The layout changes after the transition starts; redo the fade once it settles
            binding.animeSynopsis.post(() -> { if (binding != null) fadeLastLine(true); });
        }
    }

    /** Shows AniList's wording while the app keeps its own stored values (Ongoing / Completed). */
    @Nullable
    private static String displayStatus(@Nullable String s) {
        if (s == null || s.trim().isEmpty()) return null;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "ongoing":
            case "airing":     return "Releasing";
            case "completed":  return "Finished";
            case "upcoming":   return "Not yet released";
            default:           return s.trim();
        }
    }

    // ---------- Genre chips ----------

    private void showGenres(@Nullable List<String> genres) {
        binding.genreGroup.removeAllViews();
        if (genres == null || genres.isEmpty()) {
            binding.genreGroup.setVisibility(View.GONE);
            return;
        }
        binding.genreGroup.setVisibility(View.VISIBLE);
        float dp = getResources().getDisplayMetrics().density;
        for (String rawGenre : genres) {
            final String genre = rawGenre == null ? "" : rawGenre.trim();
            if (genre.isEmpty()) continue;
            TextView chip = new TextView(requireContext());
            chip.setText(genre);
            chip.setSingleLine(true);
            chip.setTextSize(13);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.ac_text));
            chip.setBackgroundResource(R.drawable.ac_genre_chip);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setMinHeight((int) (36 * dp));
            chip.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                Bundle b = new Bundle();
                b.putString("tag", genre);
                NavHostFragment.findNavController(this).navigate(R.id.tag_browse, b);
            });
            binding.genreGroup.addView(chip);
        }
    }

    // ---------- Watch / Resume ----------

    /** The episode the viewer last opened, or null if none (or it no longer exists). */
    @Nullable
    private Episode findCurrentEpisode() {
        String lastId = LibraryStore.getLastEpisode(requireContext(), animeId);
        if (lastId == null) return null;
        for (Episode e : episodes) {
            if (lastId.equals(e.getId())) return e;
        }
        return null;
    }

    private void updateResume() {
        if (binding == null) return;
        Episode current = findCurrentEpisode();
        binding.watchNowBtn.setText(current != null
                ? "Resume Ep " + current.getNumber() : "Watch now");
        episodeAdapter.setCurrentEpisodeId(current != null ? current.getId() : null);
    }

    /** First episode not marked watched (or the very first one if all are). */
    private Episode firstUnwatched() {
        for (Episode e : episodes) {
            if (!episodeAdapter.isWatched(e)) return e;
        }
        return episodes.get(0);
    }

    private void refreshStates() {
        episodeAdapter.setStates(EpisodeStore.getWatched(requireContext(), animeId),
                EpisodeStore.getBookmarks(requireContext(), animeId));
    }

    private void updateHeart() {
        boolean fav = LibraryStore.isFavorite(requireContext(), animeId);
        binding.favoriteBtn.setImageResource(
                fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void updateSaved() {
        boolean saved = LibraryStore.isSaved(requireContext(), animeId);
        binding.savedBtn.setImageResource(
                saved ? R.drawable.ic_saved_filled : R.drawable.ic_saved_outline);
    }

    private void bounce(View v) {
        v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new OvershootInterpolator(3f)).start()).start();
    }

    private void openEpisodeForm(@Nullable String episodeId) {
        Bundle args = new Bundle();
        args.putString("animeId", animeId);
        if (episodeId != null) args.putString("episodeId", episodeId);
        NavHostFragment.findNavController(this).navigate(R.id.add_episode, args);
    }

    private void openWatch(String episodeId) {
        Bundle args = new Bundle();
        args.putString("animeId", animeId);
        args.putString("episodeId", episodeId);
        NavHostFragment.findNavController(this).navigate(R.id.watch, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (discussionReg != null) { discussionReg.remove(); discussionReg = null; }
        binding = null;
    }
}
