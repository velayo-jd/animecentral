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
import com.example.animecentralapp.util.LibraryStore;

import com.example.animecentralapp.util.AppSettings;

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
import com.example.animecentralapp.util.TagUtils;
import com.example.animecentralapp.viewmodel.AnimeViewModel;
import com.example.animecentralapp.viewmodel.CommentViewModel;
import com.example.animecentralapp.viewmodel.EpisodeViewModel;

import com.example.animecentralapp.util.LibraryStore;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Watch screen: Vimeo player, autoplay bar, episode card with random recommendations
 * (based on the current anime's genres) and a collapsible comment section.
 */
public class WatchFragment extends Fragment {
    private static final int RECOMMENDATION_COUNT = 4;

    private FragmentWatchBinding binding;
    private EpisodeViewModel episodeViewModel;
    private CommentViewModel commentViewModel;
    private AnimeViewModel animeViewModel;
    private AnimeAdapter recsAdapter;
    private CommentAdapter commentAdapter;
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
    private int originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private OnBackPressedCallback fullscreenBack;

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
        // Look like normal mobile Chrome instead of an embedded WebView
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

// Some players need cookies
        android.webkit.CookieManager cookies = android.webkit.CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new android.webkit.WebViewClient());
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public android.graphics.Bitmap getDefaultVideoPoster() {
                return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            }

            // Prints any page errors to Logcat so we can see why a video fails
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.d("WebPlayer", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v,
                                                    android.webkit.WebResourceRequest req) {
                String host = req.getUrl().getHost();
                if (host != null && (host.equals("vimeo.com") || host.equals("www.vimeo.com"))) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(),
                                "This video can't be embedded. Check its Vimeo privacy settings.",
                                Toast.LENGTH_LONG).show();
                    }
                    return true; // stay in the app
                }
                return false;
            }
        });
        s.setMediaPlaybackRequiresUserGesture(false);
        // Back button leaves fullscreen first
        fullscreenBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() { exitFullscreen(); }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), fullscreenBack);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                Activity a = requireActivity();
                fullscreenView = view;
                fullscreenCallback = callback;
                originalOrientation = a.getRequestedOrientation();
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                FrameLayout decor = (FrameLayout) a.getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

                WindowInsetsControllerCompat c =
                        WindowCompat.getInsetsController(a.getWindow(), decor);
                c.hide(WindowInsetsCompat.Type.systemBars());
                c.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                fullscreenBack.setEnabled(true);
            }

            @Override
            public void onHideCustomView() { exitFullscreen(); }
        });
        web.addJavascriptInterface(new PlayerBridge(), "Android");

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

    /** Called from the Vimeo player's JavaScript (runs on a background thread). */
    private class PlayerBridge {
        @JavascriptInterface
        public void onEnded() {
            runOnUi(() -> {
                if (getContext() != null && currentIndex == episodes.size() - 1) {
                    LibraryStore.markFinished(requireContext(), animeId);
                }
                if (binding != null && binding.autoplaySwitch.isChecked()) {
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
        episodeViewModel.getEpisodes(animeId).observe(getViewLifecycleOwner(), list -> {
            episodes = list == null ? new ArrayList<>() : list;

            if (episodes.isEmpty()) {
                binding.episodeHeader.setText("No episodes");
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
        LibraryStore.markEpisode(requireContext(), animeId, episode.getId());

        binding.episodeHeader.setText("Episode " + episode.getNumber());
        commentViewModel.loadComments(animeId, episode.getId());
        loadVideo();
    }

    private void playNext() {
        if (currentIndex + 1 < episodes.size()) {
            selectEpisode(currentIndex + 1);
        }
    }

    private void loadVideo() {
        if (binding == null || currentIndex < 0 || currentIndex >= episodes.size()) return;

        Episode ep = episodes.get(currentIndex);
        String rumbleId = ep.getRumbleId() == null ? "" : ep.getRumbleId().trim();
        if (!rumbleId.isEmpty()) {
            loadRumble(rumbleId, ep.getRumblePub());
            return;
        }
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
            if (anime.getTitle() != null) binding.animeTitleText.setText(anime.getTitle());
            buildRecommendations();
        });
        animeViewModel.getAllAnime().observe(getViewLifecycleOwner(), list -> {
            allAnime = list;
            buildRecommendations();
        });
    }

    private void loadRumble(String id, String pub) {
        String p = pub == null ? "" : pub.trim();
        if (!id.matches("[A-Za-z0-9]+") || (!p.isEmpty() && !p.matches("[A-Za-z0-9]+"))) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "This episode has no valid Rumble ID yet.",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String url = "https://rumble.com/embed/" + id + "/" + (p.isEmpty() ? "" : "?pub=" + p);
        binding.vimeoWebView.loadUrl(url);
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

    private void setupComments() {
        commentAdapter = new CommentAdapter();
        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.commentsRecyclerView.setAdapter(commentAdapter);

        commentViewModel.getComments().observe(getViewLifecycleOwner(), list -> {
            int count = list == null ? 0 : list.size();
            commentAdapter.submitList(list);
            binding.commentsCount.setText(count + (count == 1 ? " Comment" : " Comments"));
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
    }

    private void exitFullscreen() {
        Activity a = getActivity();
        if (fullscreenView == null || a == null) return;

        FrameLayout decor = (FrameLayout) a.getWindow().getDecorView();
        decor.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;

        a.setRequestedOrientation(originalOrientation);
        WindowCompat.getInsetsController(a.getWindow(), decor)
                .show(WindowInsetsCompat.Type.systemBars());
        if (fullscreenBack != null) fullscreenBack.setEnabled(false);
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
        commentViewModel.postComment(animeId, episodes.get(currentIndex).getId(), text);
        binding.commentInput.setText("");
        hideKeyboard(binding.commentInput);
    }

    // ---------- Controls + helpers ----------

    private void setupControls() {
        binding.nextButton.setOnClickListener(v -> playNext());
        binding.prevButton.setOnClickListener(v -> selectEpisode(currentIndex - 1));
        binding.autoplaySwitch.setChecked(AppSettings.isAutoplay(requireContext()));
        binding.autoplaySwitch.setOnCheckedChangeListener((b, on) ->
                AppSettings.setAutoplay(requireContext(), on));
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
