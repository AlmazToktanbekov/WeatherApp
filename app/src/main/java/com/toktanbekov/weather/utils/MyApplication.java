package com.toktanbekov.weather.utils;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class MyApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    // Enforce Night mode by default
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
  }
}