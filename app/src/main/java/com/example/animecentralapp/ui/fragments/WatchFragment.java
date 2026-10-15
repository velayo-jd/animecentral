package com.example.animecentralapp.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import com.example.animecentralapp.util.EpisodeStore;

import android.webkit.WebChromeClient;
import com.example.animecentralapp.repository.CommentRepository;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentWatchBinding;
import com.example.animecentralapp.model.Anime;
import com.example.animecentralapp.model.Episode;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.AnimeAdapter;
import com.example.animecentralapp.ui.adapters.CommentAdapter;
import com.example.animecentralapp.ui.adapters.WatchEpisodeAdapter;
import com.example.animecentralapp.model.Comment;
import com.example.animecentralapp.util.AppSettings;
import com.example.animecentralapp.util.LibraryStore;
import com.bumptech.glide.Glide;
import androidx.core.content.ContextCompat;
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.example.animecentralapp.viewmodel.CommentViewModel;
import com.example.animecentralapp.viewmodel.EpisodeViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Watch screen laid out like a YouTube player: edge-to-edge video, title block, anime row,
 * action chips, comments preview, episode list and "more like this".
 */
public class WatchFragment extends Fragment {
    private static final int RECOMMENDATION_COUNT = 4;

    private FragmentWatchBinding binding;
    private EpisodeViewModel episodeViewModel;
    private CommentViewModel commentViewModel;
    private AnimeViewModel animeViewModel;
    private AnimeAdapter recsAdapter;
    private CommentAdapter commentAdapter;
    private WatchEpisodeAdapter episodeAdapter;
    private OnBackPressedCallback closeCommentsCallback;

    private String animeId;
    private String startEpisodeId;
    private List<Episode> episodes = new ArrayList<>();
    private int currentIndex = -1;

    private Anime currentAnime;
    private List<Anime> allAnime;
    private boolean recsBuilt = false;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int previousOrientation;
    private OnBackPressedCallback fullscreenBackCallback;
    private Comment replyingTo;
    private final CommentRepository commentRepo = new CommentRepository();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWatchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        animeId = args != null ? args.getString("animeId") : null;
        startEpisodeId = args != null ? args.getString("episodeId") : null;
        if (animeId == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        animeViewModel = new ViewModelProvider(this).get(AnimeViewModel.class);
        episodeViewModel = new ViewModelProvider(this).get(EpisodeViewModel.class);
        commentViewModel = new ViewModelProvider(this).get(CommentViewModel.class);

        setupTopBar();
        setupPlayer();
        setupEpisodes();
        setupRecommendations();
        setupComments();
        setupControls();
    }

    // ---------- Top bar + search box over the player ----------

    private void setupTopBar() {
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());

        binding.topBar.searchIcon.setOnClickListener(v -> {
            boolean show = binding.watchSearchOverlay.getVisibility() != View.VISIBLE;
            binding.watchSearchOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                binding.watchSearchInput.requestFocus();
                showKeyboard(binding.watchSearchInput);
            } else {
                hideKeyboard(binding.watchSearchInput);
            }
        });

        // Searching from here opens the Search screen with what was typed
        binding.watchSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = binding.watchSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    hideKeyboard(binding.watchSearchInput);
                    Bundle args = new Bundle();
                    args.putString("query", query);
                    NavHostFragment.findNavController(this).navigate(R.id.search, args);
                }
                return true;
            }
            return false;
        });
    }

    // ---------- Player ----------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupPlayer() {
        WebView web = binding.vimeoWebView;

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new PlayerBridge(), "Android");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        // Keep the player 16:9 whatever the screen width is
        web.addOnLayoutChangeListener((v, l, t, r, bm, ol, ot, or, ob) -> {
            int w = r - l;
            int wantedHeight = w * 9 / 16;
            if (w > 0 && v.getLayoutParams().height != wantedHeight) {
                v.getLayoutParams().height = wantedHeight;
                v.post(v::requestLayout);
            }
        });
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {          // already fullscreen
            callback.onCustomViewHidden();
            return;
        }
        android.app.Activity a = requireActivity();
        fullscreenView = view;
        fullscreenCallback = callback;

        view.setBackgroundColor(android.graphics.Color.BLACK);
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        decor.addView(view, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        previousOrientation = a.getRequestedOrientation();
        a.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(a.getWindow(), decor);
        c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Back leaves fullscreen first (added last, so it runs before the comments one)
        fullscreenBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitFullscreen();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), fullscreenBackCallback);
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        android.app.Activity a = requireActivity();
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();

        decor.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        if (fullscreenBackCallback != null) fullscreenBackCallback.remove();
        fullscreenBackCallback = null;

        a.setRequestedOrientation(previousOrientation);
        androidx.core.view.WindowCompat.getInsetsController(a.getWindow(), decor)
                .show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
    }

    /** Called from the Vimeo player's JavaScript (runs on a background thread). */
    private class PlayerBridge {
        @JavascriptInterface
        public void onEnded() {
            runOnUi(() -> {
                if (binding != null && currentIndex >= 0 && currentIndex < episodes.size()) {
                    String id = episodes.get(currentIndex).getId();
                    if (id != null && !com.example.animecentralapp.util.EpisodeStore
                            .getWatched(requireContext(), animeId).contains(id)) {
                        com.example.animecentralapp.util.EpisodeStore
                                .toggleWatched(requireContext(), animeId, id);
                        episodeAdapter.setWatched(com.example.animecentralapp.util.EpisodeStore
                                .getWatched(requireContext(), animeId));
                    }
                }
                if (binding != null && AppSettings.isAutoplay(requireContext())) {
                    playNext();
                }
            });
        }

        @JavascriptInterface
        public void onError(String message) {
            runOnUi(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "This video can't be played right now.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void runOnUi(Runnable r) {
        if (binding != null) binding.getRoot().post(r);
    }

    // ---------- Episodes ----------

    private void setupEpisodes() {
        episodeAdapter = new WatchEpisodeAdapter((ep, pos) -> selectEpisode(pos));

        episodeAdapter.setBookmarked(EpisodeStore.getBookmarks(requireContext(), animeId));
        episodeAdapter.setOnToggleSaved(ep -> {
            if (ep.getId() == null) return;
            boolean now = EpisodeStore.toggleBookmark(requireContext(), animeId, ep.getId());
            episodeAdapter.setBookmarked(EpisodeStore.getBookmarks(requireContext(), animeId));
            Toast.makeText(requireContext(),
                    now ? "Episode " + ep.getNumber() + " saved"
                            : "Episode " + ep.getNumber() + " removed from saved",
                    Toast.LENGTH_SHORT).show();
        });

        binding.watchEpisodesRecycler.setLayoutManager(new GridLayoutManager(getContext(), 5));
        binding.watchEpisodesRecycler.setAdapter(episodeAdapter);
        episodeViewModel.getEpisodes(animeId).observe(getViewLifecycleOwner(), list -> {
            episodes = list == null ? new ArrayList<>() : list;

            episodeAdapter.submitList(new ArrayList<>(episodes));
            updateAnimeMeta();
            if (episodes.isEmpty()) {
                binding.watchTitle.setText("No episodes");
                return;
            }
            if (currentIndex == -1) {
                selectEpisode(findStartIndex());
            }
        });
    }

    private int findStartIndex() {
        if (startEpisodeId != null) {
            for (int i = 0; i < episodes.size(); i++) {
                if (startEpisodeId.equals(episodes.get(i).getId())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void selectEpisode(int index) {
        if (binding == null || index < 0 || index >= episodes.size()) return;

        currentIndex = index;
        Episode episode = episodes.get(index);

        String t = episode.getTitle();
        boolean hasTitle = t != null && !t.trim().isEmpty();
        binding.watchTitle.setText(hasTitle
                ? "Episode " + episode.getNumber() + " · " + t.trim()
                : "Episode " + episode.getNumber());
        binding.watchSubtitle.setText(currentAnime != null && currentAnime.getTitle() != null
                ? currentAnime.getTitle() : "");
        episodeAdapter.setWatched(com.example.animecentralapp.util.EpisodeStore.getWatched(requireContext(), animeId));

        episodeAdapter.setBookmarked(
                com.example.animecentralapp.util.EpisodeStore.getBookmarks(requireContext(), animeId));

        episodeAdapter.setCurrentEpisodeId(episode.getId());
        LibraryStore.recordWatch(requireContext(), animeId, episode.getId());
        updateNavChips();
        commentViewModel.loadComments(animeId, episode.getId());
        loadVideo();
        clearReply();
    }

    private void playNext() {
        if (binding == null) return;
        if (currentIndex + 1 < episodes.size()) {
            selectEpisode(currentIndex + 1);
        }
    }

    private void loadVideo() {
        if (binding == null || currentIndex < 0 || currentIndex >= episodes.size()) return;

        Episode ep = episodes.get(currentIndex);
        String id = ep.getVimeoId() == null ? "" : ep.getVimeoId().trim();
        String hash = ep.getVimeoHash() == null ? "" : ep.getVimeoHash().trim();

        // Only digits for the id and letters/digits for the hash, so nothing odd ends up in the HTML
        if (!id.matches("\\d+") || (!hash.isEmpty() && !hash.matches("[A-Za-z0-9]+"))) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "This episode has no valid Vimeo ID yet.",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String url = "https://player.vimeo.com/video/" + id
                + "?autoplay=1&playsinline=1&title=0&byline=0&portrait=0"
                + (hash.isEmpty() ? "" : "&h=" + hash);

        String html = "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000}"
                + "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>"
                + "</head><body>"
                + "<iframe id='p' src='" + url + "' "
                + "allow='autoplay; fullscreen; picture-in-picture' allowfullscreen></iframe>"
                + "<script src='https://player.vimeo.com/api/player.js'></script>"
                + "<script>"
                + "var player = new Vimeo.Player(document.getElementById('p'));"
                + "player.on('ended', function(){ Android.onEnded(); });"
                + "player.on('error', function(e){ Android.onError(e && e.message ? e.message : 'error'); });"
                + "</script></body></html>";

        // The base URL gives the page a real origin so the embedded player accepts it
        binding.vimeoWebView.loadDataWithBaseURL("https://com.example.animecentralapp",
                html, "text/html", "UTF-8", null);
    }

    // ---------- Recommendations (random, based on the current anime's genres) ----------

    private void setupRecommendations() {
        recsAdapter = new AnimeAdapter(this::openAnime, 170);
        binding.recsRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recsRecycler.setAdapter(recsAdapter);
        binding.recsRecycler.setNestedScrollingEnabled(false);

        animeViewModel.getAnimeById(animeId).observe(getViewLifecycleOwner(), anime -> {
            if (anime == null || binding == null) return;
            currentAnime = anime;
            if (anime.getTitle() != null) {
                binding.animeTitleText.setText(anime.getTitle());
                binding.watchSubtitle.setText(anime.getTitle());
            }
            String img = anime.getPosterUrl() != null && !anime.getPosterUrl().trim().isEmpty()
                    ? anime.getPosterUrl() : anime.getThumbnailUrl();
            if (img != null && !img.trim().isEmpty()) {
                Glide.with(this).load(img.trim()).into(binding.watchAvatar);
            }
            updateFavChip();
            buildRecommendations();
        });
        animeViewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            allAnime = list;
            buildRecommendations();
        });
    }

    /** Picks once per visit, so the squares don't reshuffle while you're watching. */
    private void buildRecommendations() {
        if (recsBuilt || currentAnime == null || allAnime == null) return;
        recsBuilt = true;
        recsAdapter.submitList(TagUtils.relatedTo(currentAnime, allAnime, RECOMMENDATION_COUNT));
    }

    private void openAnime(Anime anime) {
        Bundle args = new Bundle();
        args.putString("animeId", anime.getId());
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    // ---------- Comments ----------

    private void startReply(Comment target) {
        replyingTo = target;
        String name = target.getUsername() == null || target.getUsername().isEmpty()
                ? "Guest" : target.getUsername();
        binding.replyBannerText.setText("Replying to " + name);
        binding.replyBanner.setVisibility(View.VISIBLE);
        boolean toReply = target.getParentId() != null && !target.getParentId().isEmpty();
        binding.commentInput.setText(toReply ? "@" + name + " " : "");
        binding.commentInput.setSelection(binding.commentInput.getText().length());
        binding.commentInput.requestFocus();
        showKeyboard(binding.commentInput);
    }

    private void clearReply() {
        replyingTo = null;
        if (binding != null) binding.replyBanner.setVisibility(View.GONE);
    }

    private void setupComments() {
        commentAdapter = new CommentAdapter();
        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.commentsRecyclerView.setAdapter(commentAdapter);

        commentViewModel.getComments().observe(getViewLifecycleOwner(), list -> {
            int count = list == null ? 0 : list.size();
            commentAdapter.submitComments(list);
            commentAdapter.setOnReply(this::startReply);
            commentAdapter.setOnModerate((comment, replyCount) -> {
                if (currentIndex < 0 || currentIndex >= episodes.size()) return;
                final String episodeId = episodes.get(currentIndex).getId();
                final String id = comment.getCommentId();
                if (id == null || episodeId == null) return;

                String hideLabel = comment.isHidden() ? "Unhide comment" : "Hide comment";
                String deleteLabel = replyCount > 0
                        ? "Delete comment and " + replyCount + (replyCount == 1 ? " reply" : " replies")
                        : "Delete comment";

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(comment.getUsername() == null ? "Comment" : comment.getUsername())
                        .setItems(new String[]{hideLabel, deleteLabel}, (d, which) -> {
                            if (which == 0) {
                                commentRepo.setHidden(animeId, episodeId, id, !comment.isHidden());
                            } else {
                                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                        .setMessage("Delete this permanently?")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("Delete", (d2, w) ->
                                                commentRepo.deleteThread(animeId, episodeId, id))
                                        .show();
                            }
                        })
                        .show();
            });
            binding.replyBannerCancel.setOnClickListener(v -> clearReply());
            String label = count + (count == 1 ? " Comment" : " Comments");
            binding.commentsCount.setText(label);
            binding.commentsPreviewCount.setText(label);
            if (count > 0) {
                Comment c = list.get(0);
                binding.commentsPreviewText.setText(c.getText());
                if (c.getUserPhotoUrl() != null && !c.getUserPhotoUrl().isEmpty()) {
                    Glide.with(this).load(c.getUserPhotoUrl()).into(binding.commentsPreviewAvatar);
                } else {
                    binding.commentsPreviewAvatar.setImageDrawable(null);
                }
            } else {
                binding.commentsPreviewText.setText("Add a comment…");
                binding.commentsPreviewAvatar.setImageDrawable(null);
            }
        });

        // Back closes the open comments first
        closeCommentsCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showComments(false);
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), closeCommentsCallback);

        binding.commentsBar.setOnClickListener(v -> showComments(true));
        binding.commentsHeader.setOnClickListener(v -> showComments(false));

        binding.postCommentBtn.setOnClickListener(v -> postComment());
        binding.commentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                postComment();
                return true;
            }
            return false;
        });
    }

    private void showComments(boolean show) {
        if (binding == null) return;
        binding.commentsCard.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.modeAScroll.setVisibility(show ? View.GONE : View.VISIBLE);
        closeCommentsCallback.setEnabled(show);
        if (!show) hideKeyboard(binding.commentInput);
        clearReply();
    }

    private void postComment() {
        if (currentIndex < 0 || currentIndex >= episodes.size()) {
            Toast.makeText(requireContext(), "Pick an episode first", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = binding.commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "Comment cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String parentId = null;
        if (replyingTo != null) {
            boolean toReply = replyingTo.getParentId() != null && !replyingTo.getParentId().isEmpty();
            parentId = toReply ? replyingTo.getParentId() : replyingTo.getCommentId();
        }

        commentViewModel.postComment(animeId, episodes.get(currentIndex).getId(), text, parentId);
        commentAdapter.expand(parentId);   // open the thread so you see your reply
        clearReply();
        binding.commentInput.setText("");
        hideKeyboard(binding.commentInput);
    }

    // ---------- Controls + helpers ----------

    private void setupControls() {
        binding.nextButton.setOnClickListener(v -> playNext());
        binding.prevButton.setOnClickListener(v -> selectEpisode(currentIndex - 1));

        updateAutoplayChip();
        binding.autoplayChip.setOnClickListener(v -> {
            AppSettings.setAutoplay(requireContext(), !AppSettings.isAutoplay(requireContext()));
            updateAutoplayChip();
        });

        binding.watchAnimeRow.setOnClickListener(v -> openDetails());

        updateFavChip();
        binding.watchFavChip.setOnClickListener(v -> {
            LibraryStore.toggleFavorite(requireContext(), animeId);
            updateFavChip();
        });

        updateSavedChip();
        binding.watchSavedChip.setOnClickListener(v -> {
            LibraryStore.toggleSaved(requireContext(), animeId);
            updateSavedChip();
        });
    }

    private void openDetails() {
        Bundle args = new Bundle();
        args.putString("animeId", animeId);
        NavHostFragment.findNavController(this).navigate(R.id.details, args);
    }

    private void updateAutoplayChip() {
        if (binding == null) return;
        boolean on = AppSettings.isAutoplay(requireContext());
        binding.autoplayChip.setText("Autoplay: " + (on ? "On" : "Off"));
        binding.autoplayChip.setBackgroundResource(on ? R.drawable.ac_watch_chip_on : R.drawable.ac_watch_chip);
        binding.autoplayChip.setTextColor(ContextCompat.getColor(requireContext(),
                on ? R.color.ac_on_accent : R.color.ac_text));
    }

    private void updateFavChip() {
        if (binding == null) return;
        boolean fav = LibraryStore.isFavorite(requireContext(), animeId);
        binding.watchFavChip.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        binding.watchFavChip.setColorFilter(fav
                ? android.graphics.Color.parseColor("#E5484D")
                : ContextCompat.getColor(requireContext(), R.color.ac_text));
    }

    private void updateSavedChip() {
        if (binding == null) return;
        boolean saved = LibraryStore.isSaved(requireContext(), animeId);
        binding.watchSavedChip.setImageResource(
                saved ? R.drawable.ic_saved_filled : R.drawable.ic_saved_outline);
        binding.watchSavedChip.setColorFilter(ContextCompat.getColor(requireContext(),
                saved ? R.color.ac_accent : R.color.ac_text));
    }

    private void updateNavChips() {
        if (binding == null) return;
        binding.prevButton.setAlpha(currentIndex > 0 ? 1f : 0.4f);
        binding.nextButton.setAlpha(currentIndex + 1 < episodes.size() ? 1f : 0.4f);
    }

    private void updateAnimeMeta() {
        if (binding == null) return;
        int n = episodes.size();
        binding.watchAnimeMeta.setText(n + (n == 1 ? " episode" : " episodes"));
    }

    private void showKeyboard(View target) {
        target.post(() -> {
            if (getContext() == null) return;
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard(View target) {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        exitFullscreen();
        if (binding != null) binding.vimeoWebView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) binding.vimeoWebView.onResume();
    }

    @Override
    public void onDestroyView() {
        exitFullscreen();
        super.onDestroyView();
        if (binding != null) {
            WebView web = binding.vimeoWebView;
            web.loadUrl("about:blank");
            web.removeJavascriptInterface("Android");
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
        }
        currentIndex = -1;
        recsBuilt = false;
        binding = null;
    }
}
