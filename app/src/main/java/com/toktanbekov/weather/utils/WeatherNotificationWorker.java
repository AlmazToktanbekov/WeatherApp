package com.toktanbekov.weather.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.Locale;

public class WeatherNotificationWorker extends Worker {

    public WeatherNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            String lastCity = getLastCityFromPrefs();

            DatabaseHelper.WeatherData weatherData = dbHelper.getWeatherData(lastCity);
            if (weatherData != null) {
                WeatherNotificationManager notificationManager = new WeatherNotificationManager(getApplicationContext());
                notificationManager.showImmediateNotification(weatherData);
                return Result.success();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.retry();
    }

    private String getLastCityFromPrefs() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);
        return prefs.getString("last_city", "Бишкек");
    }
}