package com.example.animecentralapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentComingSoonBinding;
import com.example.animecentralapp.ui.MainActivity;

/** Temporary screen for tabs that aren't built yet (Videos, History, Settings). */
public class ComingSoonFragment extends Fragment {
    private FragmentComingSoonBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentComingSoonBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        String title = args != null ? args.getString("title", "") : "";
        binding.topBar.menuBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDrawer());
        binding.topBar.searchIcon.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.search));
        binding.comingTitle.setText(title);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
