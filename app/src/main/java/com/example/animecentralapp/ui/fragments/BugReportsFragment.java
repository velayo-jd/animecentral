package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentBugReportsBinding;
import com.example.animecentralapp.repository.DiscussionRepository;
import com.example.animecentralapp.ui.MainActivity;
import com.example.animecentralapp.ui.adapters.DiscussionAdapter;
import com.example.animecentralapp.util.NewDiscussionSheet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

/** The app-wide bug board: same threads as the Discussion tab, stored in "bugReports". */
public class BugReportsFragment extends Fragment {
    private FragmentBugReportsBinding binding;
    private DiscussionAdapter adapter;
    private ListenerRegistration reg;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBugReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));

        adapter = new DiscussionAdapter(thread -> {
            Bundle b = new Bundle();
            b.putString("animeId", DiscussionRepository.BUGS);   // "BUGS" = the bug board
            b.putString("threadId", thread.getId());
            NavHostFragment.findNavController(this).navigate(R.id.discussion, b);
        });
        binding.reportsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.reportsRecyclerView.setAdapter(adapter);
        binding.reportsRecyclerView.setNestedScrollingEnabled(false);

        binding.reportBugBtn.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(requireContext(), "Sign in to report a bug",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            NewDiscussionSheet.show(requireContext(), DiscussionRepository.BUGS, null);
        });

        reg = new DiscussionRepository().listenThreads(DiscussionRepository.BUGS, list -> {
            if (binding == null) return;
            adapter.submitList(list, () -> {
                if (binding != null) {
                    binding.noReportsText.setVisibility(
                            adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) { reg.remove(); reg = null; }
        binding = null;
    }
}