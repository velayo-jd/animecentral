package com.example.animecentralapp;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.animecentralapp.util.AppSettings;

public class AnimeCentralApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppSettings.isLight(this)
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }
}