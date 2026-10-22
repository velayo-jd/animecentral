package com.example.animecentralapp.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentDiscussionBinding;
import com.example.animecentralapp.model.Discussion;
import com.example.animecentralapp.model.DiscussionReply;
import com.example.animecentralapp.repository.DiscussionRepository;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.DiscussionReplyAdapter;
import com.example.animecentralapp.util.AdminAccess;
import com.example.animecentralapp.util.TimeAgo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/** One thread with its replies. Opened with a Bundle: animeId, threadId. */
public class DiscussionFragment extends Fragment {
    private FragmentDiscussionBinding binding;
    private final DiscussionRepository repo = new DiscussionRepository();
    private DiscussionReplyAdapter replyAdapter;
    private ListenerRegistration threadReg, repliesReg;

    private String animeId, threadId;
    private boolean bodyRevealed = false;
    private boolean replySpoiler = false;
    private boolean leaving = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscussionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        animeId = args != null ? args.getString("animeId") : null;
        threadId = args != null ? args.getString("threadId") : null;
        if (animeId == null || threadId == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        replyAdapter = new DiscussionReplyAdapter();
        binding.repliesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.repliesRecyclerView.setAdapter(replyAdapter);
        binding.repliesRecyclerView.setNestedScrollingEnabled(false);
        replyAdapter.setOnLongPress(this::confirmDeleteReply);

        binding.threadBodyCover.setOnClickListener(v -> {
            bodyRevealed = true;
            binding.threadBodyCover.setVisibility(View.GONE);
            binding.threadBody.setVisibility(View.VISIBLE);
        });

        binding.replySpoilerToggle.setOnClickListener(v -> {
            replySpoiler = !replySpoiler;
            updateSpoilerToggle();
        });
        binding.sendBtn.setOnClickListener(v -> sendReply());
        binding.threadDeleteBtn.setOnClickListener(v -> confirmDeleteThread());
        updateSpoilerToggle();

        threadReg = repo.listenThread(animeId, threadId, this::showThread);
        repliesReg = repo.listenReplies(animeId, threadId, list -> {
            if (binding == null) return;
            replyAdapter.submitList(list);
            binding.noRepliesText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    // ---------- thread ----------

    private void showThread(@Nullable Discussion d) {
        if (binding == null) return;
        if (d == null) {                       // deleted (by you or someone else)
            if (!leaving) {
                leaving = true;
                Toast.makeText(requireContext(), "This thread was deleted",
                        Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }

        binding.threadTitle.setText(d.getTitle());
        String name = d.getUsername() == null || d.getUsername().isEmpty()
                ? "Guest" : d.getUsername();
        binding.threadMeta.setText(name + "  ·  " + TimeAgo.ago(d.getTimestamp()));
        binding.threadSpoilerTag.setVisibility(d.isSpoiler() ? View.VISIBLE : View.GONE);

        String body = d.getBody() == null ? "" : d.getBody().trim();
        binding.threadBody.setText(body);
        boolean hide = d.isSpoiler() && !bodyRevealed && !body.isEmpty();
        binding.threadBodyCover.setVisibility(hide ? View.VISIBLE : View.GONE);
        binding.threadBody.setVisibility(hide || body.isEmpty() ? View.GONE : View.VISIBLE);

        int n = d.getReplyCount();
        binding.threadRepliesCount.setText(n <= 0 ? "Replies" : n + (n == 1 ? " reply" : " replies"));
        binding.threadDeleteBtn.setVisibility(canModify(d.getUserId()) ? View.VISIBLE : View.GONE);
    }

    private boolean canModify(@Nullable String authorId) {
        if (AdminAccess.isAdmin()) return true;
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u != null && authorId != null && authorId.equals(u.getUid());
    }

    // ---------- replying ----------

    private void updateSpoilerToggle() {
        binding.replySpoilerToggle.setTextColor(replySpoiler
                ? Color.parseColor("#E5484D")
                : ContextCompat.getColor(requireContext(), R.color.ac_text_dim));
    }

    private void sendReply() {
        String text = binding.replyInput.getText().toString().trim();
        if (text.isEmpty()) return;

        DiscussionReply reply = DiscussionRepository.newReply(requireContext(), text, replySpoiler);
        if (reply == null) {
            Toast.makeText(requireContext(), "Sign in to reply", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.sendBtn.setEnabled(false);
        repo.addReply(animeId, threadId, reply, (ok, error) -> {
            if (binding == null) return;
            binding.sendBtn.setEnabled(true);
            if (ok) {
                binding.replyInput.setText("");
                replySpoiler = false;
                updateSpoilerToggle();
                binding.discussionScroll.postDelayed(() -> {
                    if (binding != null) binding.discussionScroll.fullScroll(View.FOCUS_DOWN);
                }, 400);
            } else {
                Toast.makeText(requireContext(), "Couldn't reply: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- deleting ----------

    private void confirmDeleteThread() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete this thread?")
                .setMessage("The thread and all its replies are removed for everyone.")
                .setPositiveButton("Delete", (d, w) -> {
                    leaving = true; // we leave ourselves below, skip the "was deleted" toast
                    repo.deleteThread(animeId, threadId, (ok, error) -> {
                        if (getContext() == null) return;
                        if (ok) {
                            Toast.makeText(getContext(), "Thread deleted", Toast.LENGTH_SHORT).show();
                            if (binding != null) {
                                NavHostFragment.findNavController(this).popBackStack();
                            }
                        } else {
                            leaving = false;
                            Toast.makeText(getContext(), "Couldn't delete: " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteReply(DiscussionReply reply) {
        if (!canModify(reply.getUserId()) || reply.getId() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete this reply?")
                .setPositiveButton("Delete", (d, w) ->
                        repo.deleteReply(animeId, threadId, reply.getId(), (ok, error) -> {
                            if (!ok && getContext() != null) {
                                Toast.makeText(getContext(), "Couldn't delete: " + error,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (threadReg != null) { threadReg.remove(); threadReg = null; }
        if (repliesReg != null) { repliesReg.remove(); repliesReg = null; }
        binding = null;
    }
}