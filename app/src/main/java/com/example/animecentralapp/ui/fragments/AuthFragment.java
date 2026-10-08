package com.example.animecentralapp.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.animecentralapp.R;
import com.example.animecentralapp.databinding.FragmentAuthBinding;
import com.example.animecentralapp.util.AppSettings;
import com.example.animecentralapp.util.LibraryStore;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One screen for both Log in and Sign up (destinations "login" and "signup" in the nav graph).
 * Sign up needs a unique username, an email and a password. Log in uses email + password.
 */
public class AuthFragment extends Fragment {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private FragmentAuthBinding binding;
    private boolean signUp;
    private boolean busy;

    /** Receives the result of the Google account picker. */
    private final ActivityResultLauncher<Intent> googleLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    firebaseSignInWithGoogle(account.getIdToken());
                } catch (ApiException e) {
                    setBusy(false);
                    // 12501 = the user closed the picker
                    if (e.getStatusCode() != 12501) {
                        showError("Google sign-in failed (code " + e.getStatusCode() + ")");
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        signUp = NavHostFragment.findNavController(this).getCurrentDestination() != null
                && NavHostFragment.findNavController(this).getCurrentDestination().getId()
                == R.id.signup;

        binding.authTitle.setText(signUp ? "Create account" : "Welcome back");
        binding.authSubtitle.setText(signUp
                ? "Sign up to get your own username" : "Log in to your account");
        binding.inputUsername.setVisibility(signUp ? View.VISIBLE : View.GONE);
        binding.inputConfirm.setVisibility(signUp ? View.VISIBLE : View.GONE);
        binding.forgotPassword.setVisibility(signUp ? View.GONE : View.VISIBLE);
        binding.authButton.setText(signUp ? "SIGN UP" : "LOG IN");
        binding.authSwitch.setText(signUp
                ? "Already have an account?  Log in" : "New here?  Create an account");

        binding.authButton.setOnClickListener(v -> submit());
        binding.authSwitch.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            int from = signUp ? R.id.signup : R.id.login;
            int to = signUp ? R.id.login : R.id.signup;
            nav.navigate(to, null, new NavOptions.Builder().setPopUpTo(from, true).build());
        });
        binding.googleButton.setOnClickListener(v -> startGoogleSignIn());
        binding.forgotPassword.setOnClickListener(v -> sendReset());
        binding.authGuest.setOnClickListener(v -> {
            AppSettings.setSeenLogin(requireContext(), true);
            goHome();
        });
    }

    // ---------- Log in / Sign up ----------

    private void submit() {
        if (busy) return;
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Enter a valid email address");
            return;
        }
        if (signUp) {
            String username = binding.inputUsername.getText().toString().trim();
            if (!USERNAME.matcher(username).matches()) {
                showError("Username: 3-20 letters, numbers or underscores");
                return;
            }
            if (password.length() < 6) {
                showError("Password must be at least 6 characters");
                return;
            }
            if (!password.equals(binding.inputConfirm.getText().toString())) {
                showError("Passwords don't match");
                return;
            }
            checkUsernameThenCreate(username, email, password);
        } else {
            if (password.isEmpty()) {
                showError("Enter your password");
                return;
            }
            setBusy(true);
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(r -> onSuccess())
                    .addOnFailureListener(e -> fail(e));
        }
    }

    private void checkUsernameThenCreate(String username, String email, String password) {
        setBusy(true);
        String key = username.toLowerCase(Locale.ROOT);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("usernames").document(key).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        setBusy(false);
                        showError("That username is taken");
                        return;
                    }
                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(result -> saveProfile(
                                    result.getUser(), username, key, email))
                            .addOnFailureListener(this::fail);
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    showError("Couldn't check the username. Check your connection.");
                });
    }

    /** Saves the username on the account and claims it in Firestore. */
    private void saveProfile(@Nullable FirebaseUser user, String username, String key, String email) {
        if (user == null) {
            fail(new IllegalStateException("No user"));
            return;
        }
        user.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(username).build());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> claim = new HashMap<>();
        claim.put("uid", user.getUid());
        db.collection("usernames").document(key).set(claim);

        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", email);
        profile.put("username", username);
        profile.put("displayName", username);
        profile.put("createdAt", System.currentTimeMillis());
        db.collection("users").document(user.getUid()).set(profile);

        onSuccess();
    }

    private void onSuccess() {
        if (binding == null) return;
        // Use the account's name instead of any name picked as a guest
        LibraryStore.setName(requireContext(), "");
        AppSettings.setSeenLogin(requireContext(), true);
        Toast.makeText(requireContext(), signUp ? "Account created" : "Logged in",
                Toast.LENGTH_SHORT).show();
        goHome();
    }

    private void sendReset() {
        String email = binding.inputEmail.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Type your email above, then tap Forgot password");
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Reset link sent. Check your email.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(this::fail);
    }

    // ---------- Google ----------

    private void startGoogleSignIn() {
        if (busy) return;
        setBusy(true);
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(requireActivity(), options);
        // Sign out of the Google client first so the account picker always shows
        client.signOut().addOnCompleteListener(t ->
                googleLauncher.launch(client.getSignInIntent()));
    }

    private void firebaseSignInWithGoogle(@Nullable String idToken) {
        if (idToken == null) {
            setBusy(false);
            showError("Google sign-in failed (no token)");
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    boolean isNew = result.getAdditionalUserInfo() != null
                            && result.getAdditionalUserInfo().isNewUser();
                    if (user != null && isNew) {
                        // First time with this Google account: save a profile document
                        Map<String, Object> profile = new HashMap<>();
                        profile.put("uid", user.getUid());
                        profile.put("email", user.getEmail());
                        profile.put("displayName", user.getDisplayName());
                        profile.put("createdAt", System.currentTimeMillis());
                        FirebaseFirestore.getInstance().collection("users")
                                .document(user.getUid()).set(profile);
                    }
                    onSuccess();
                })
                .addOnFailureListener(this::fail);
    }

    // ---------- helpers ----------

    private void goHome() {
        NavHostFragment.findNavController(this).navigate(R.id.home, null,
                new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
    }

    private void fail(Exception e) {
        if (binding == null) return;
        setBusy(false);
        String code = e instanceof FirebaseAuthException
                ? ((FirebaseAuthException) e).getErrorCode() : "";
        switch (code) {
            case "ERROR_EMAIL_ALREADY_IN_USE":
                showError("That email already has an account");
                break;
            case "ERROR_WEAK_PASSWORD":
                showError("Password is too weak (use 6+ characters)");
                break;
            case "ERROR_INVALID_EMAIL":
                showError("That email address isn't valid");
                break;
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_INVALID_CREDENTIAL":
            case "ERROR_INVALID_LOGIN_CREDENTIALS":
                showError("Wrong email or password");
                break;
            default:
                // Newer Firebase versions word these as plain messages
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                if (msg.contains("password is invalid") || msg.contains("credential")
                        || msg.contains("no user record")) {
                    showError("Wrong email or password");
                } else if (msg.contains("network")) {
                    showError("No connection. Check your internet.");
                } else {
                    showError("Something went wrong. Try again.");
                }
        }
    }

    private void showError(String message) {
        if (binding == null) return;
        binding.authError.setText(message);
        binding.authError.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        if (binding == null) return;
        if (busy) binding.authError.setVisibility(View.GONE);
        binding.authButton.setAlpha(busy ? 0.5f : 1f);
        binding.authButton.setText(busy ? "PLEASE WAIT..." : (signUp ? "SIGN UP" : "LOG IN"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
