package com.toktanbekov.weather.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.toktanbekov.weather.R;
import com.toktanbekov.weather.ui.activity.MainActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeatherNotificationManager {
    private Context context;
    private NotificationManager notificationManager;
    private static final String CHANNEL_ID = "weather_channel";
    private static final long NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000; // 30 минут

    public WeatherNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    public void showNotificationOnAppStart() {
        SharedPreferences prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);

        // Проверяем cooldown
        long lastNotificationTime = prefs.getLong("last_notification_time", 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastNotificationTime > NOTIFICATION_COOLDOWN_MS) {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            String lastCity = getLastCityFromPrefs();
            DatabaseHelper.WeatherData weatherData = dbHelper.getWeatherData(lastCity);

            if (weatherData != null) {
                showImmediateNotification(weatherData);
                prefs.edit().putLong("last_notification_time", currentTime).apply();
            }
        }
    }

    public void showImmediateNotification(DatabaseHelper.WeatherData data) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String notificationText = createNotificationText(data, currentTime);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(getNotificationIcon(data.weatherCode))
                .setContentTitle("🌤️ Погода в " + data.city + " • " + currentTime)
                .setContentText(notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private String createNotificationText(DatabaseHelper.WeatherData data, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("⏰ ").append(time).append("\n");
        sb.append("🌡️ Сейчас: ").append((int)data.temperature).append("°C\n");
        sb.append("☁️ ").append(data.condition).append("\n");
        sb.append("📊 Днем: ").append((int)data.maxTemp).append("°C");
        sb.append(" • Ночью: ").append((int)data.minTemp).append("°C\n");
        sb.append("💧 Влажность: ").append(data.humidity).append("%\n");
        sb.append("💨 Ветер: ").append((int)data.windSpeed).append(" км/ч");
        sb.append("\n\n👕 ").append(getClothingRecommendation(data.temperature));
        return sb.toString();
    }

    private String getClothingRecommendation(double temperature) {
        if (temperature >= 25) return "Легкая одежда, шорты, футболка";
        else if (temperature >= 20) return "Футболка, легкие брюки";
        else if (temperature >= 15) return "Кофта или ветровка";
        else if (temperature >= 10) return "Теплая кофта, куртка";
        else if (temperature >= 0) return "Теплая куртка, шапка";
        else return "Зимняя куртка, шапка, перчатки";
    }

    private int getNotificationIcon(int weatherCode) {
        if (weatherCode == 0) return R.drawable.ic_clear_day;
        else if (weatherCode >= 1 && weatherCode <= 3) return R.drawable.ic_cloudy_weather;
        else if (weatherCode >= 51 && weatherCode <= 67) return R.drawable.ic_rainy_weather;
        else if (weatherCode >= 71 && weatherCode <= 77) return R.drawable.ic_snow_weather;
        else if (weatherCode >= 80 && weatherCode <= 82) return R.drawable.ic_shower_rain;
        else if (weatherCode >= 95 && weatherCode <= 99) return R.drawable.ic_storm_weather;
        else return R.drawable.ic_unknown;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Уведомления о погоде",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Уведомления о текущей погоде и прогнозе");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String getLastCityFromPrefs() {
        SharedPreferences prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);
        return prefs.getString("last_city", "Бишкек");
    }
}