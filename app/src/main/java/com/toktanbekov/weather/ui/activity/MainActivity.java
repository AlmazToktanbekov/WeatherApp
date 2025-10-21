package com.toktanbekov.weather.ui.activity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.location.Location;
import android.location.LocationManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.toktanbekov.weather.R;
import com.toktanbekov.weather.utils.DatabaseHelper;
import com.toktanbekov.weather.utils.WeatherNotificationManager;
import com.toktanbekov.weather.utils.WeatherNotificationWorker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // UI Components
    private DatabaseHelper databaseHelper;
    private AutoCompleteTextView searchEditText;
    private ImageView searchIcon;
    private TextView cityNameText;
    private TextView currentTemperature;
    private TextView minTemperature;
    private TextView maxTemperature;
    private TextView currentDateTime;
    private LottieAnimationView weatherAnimation;
    private TextView weatherCondition;
    private TextView humidityValue;
    private TextView windValue;
    private LineChart temperatureChart;
    private Button nextDaysButton;
    private LinearLayout hourlyTimeline;

    private Handler timeHandler = new Handler();
    private Runnable timeUpdater;

    private int scrollPosition = 0;

    private ArrayAdapter<String> citiesAdapter;
    private List<String> citySuggestions = new ArrayList<>();

    private String currentCity = "Бишкек";
    private double currentLat = 42.8746;
    private double currentLon = 74.5698;
    private String currentTimezone = "Asia/Bishkek";


    private static final long NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000; // 30 минут между уведомлениями

        @Override
        protected void onResume() {
            super.onResume();

            // Очищаем старые данные раз в неделю
            new Thread(() -> {
                if (databaseHelper != null) {
                    databaseHelper.cleanupOldData();
                }
            }).start();

            // ДОБАВЛЯЕМ: Показываем уведомление при входе
            showNotificationOnAppStart();
        }

        // ДОБАВЛЯЕМ: Метод для показа уведомления при входе
        private void showNotificationOnAppStart() {
            SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);

            // Проверяем, не показывали ли уведомление недавно
            long lastNotificationTime = prefs.getLong("last_notification_time", 0);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastNotificationTime > NOTIFICATION_COOLDOWN_MS) {
                // Показываем уведомление
                if (databaseHelper != null) {
                    String lastCity = getLastCityFromPrefs();
                    DatabaseHelper.WeatherData weatherData = databaseHelper.getWeatherData(lastCity);

                    if (weatherData != null) {
                        showImmediateNotification(weatherData);
                        // Сохраняем время последнего уведомления
                        prefs.edit().putLong("last_notification_time", currentTime).apply();
                    }
                }
            }
        }

        // ДОБАВЛЯЕМ: Метод для немедленного показа уведомления
        private void showImmediateNotification(DatabaseHelper.WeatherData data) {
            // Проверяем разрешение для Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
                    return; // Нет разрешения - не показываем уведомление
                }
            }

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = "weather_channel";
            createNotificationChannel(notificationManager, channelId);

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            String notificationText = createNotificationText(data, currentTime);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
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

        // ДОБАВЛЯЕМ: Вспомогательные методы для уведомлений
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

        private void createNotificationChannel(NotificationManager notificationManager, String channelId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "Уведомления о погоде",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Уведомления о текущей погоде и прогнозе");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 500, 200, 500});
                notificationManager.createNotificationChannel(channel);
            }
        }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        databaseHelper = new DatabaseHelper(this);

        initViews();
        setupSearchAutocomplete();
        setupListeners();
        startTimeUpdater();

        // Запрос разрешения на уведомления (для Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        loadLastCityAndWeather();
        scheduleDailyNotification();
    }

    private void loadLastCityAndWeather() {
        String lastCity = getLastCityFromPrefs();

        // Сначала проверяем, есть ли данные в кэше
        if (databaseHelper != null) {
            DatabaseHelper.WeatherData lastData = databaseHelper.getWeatherData(lastCity);

            if (lastData != null) {
                // Показываем сохраненные данные сразу
                currentCity = lastData.city;
                currentLat = lastData.latitude;
                currentLon = lastData.longitude;
                if (lastData.timezone != null && !lastData.timezone.isEmpty()) {
                    currentTimezone = lastData.timezone;
                }
                searchEditText.setText(lastData.city);
                showLastSavedData();

                // Затем пытаемся обновить из сети
                if (isNetworkAvailable()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        fetchAndUpdateWeather(lastData.city);
                    }, 500);
                }
                return;
            }
        }

        // Если нет сохраненных данных, используем Бишкек по умолчанию
        if (isNetworkAvailable()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                fetchAndUpdateWeather("Бишкек");
            }, 500);
        } else {
            showError("Нет подключения к интернету и сохраненных данных");
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void initViews() {
        searchEditText = findViewById(R.id.search_edit_text);
        searchIcon = findViewById(R.id.search_icon);
        cityNameText = findViewById(R.id.city_name_text);
        currentTemperature = findViewById(R.id.current_temperature);
        minTemperature = findViewById(R.id.min_temperature);
        maxTemperature = findViewById(R.id.max_temperature);
        currentDateTime = findViewById(R.id.current_date_time);
        weatherAnimation = findViewById(R.id.weather_animation);
        weatherCondition = findViewById(R.id.weather_condition);
        humidityValue = findViewById(R.id.humidity_value);
        windValue = findViewById(R.id.wind_value);
        temperatureChart = findViewById(R.id.temperature_chart);
        nextDaysButton = findViewById(R.id.next_days_button);
        hourlyTimeline = findViewById(R.id.hourly_timeline);
    }

    private void setupSearchAutocomplete() {
        citiesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, citySuggestions);
        searchEditText.setAdapter(citiesAdapter);

        searchEditText.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            String cityName = selected.split(",")[0].trim();
            searchEditText.setText(cityName);
            performSearch();
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            private Timer timer = new Timer();
            private final long DELAY = 300;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 2) {
                    timer.cancel();
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            fetchCitySuggestions(s.toString());
                        }
                    }, DELAY);
                }
            }
        });
    }

    private void fetchCitySuggestions(String query) {
        new Thread(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                        java.net.URLEncoder.encode(query, "UTF-8") + "&count=5&language=ru";

                JSONObject response = new JSONObject(httpGet(url));
                JSONArray results = response.optJSONArray("results");

                List<String> newSuggestions = new ArrayList<>();
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject city = results.getJSONObject(i);
                        String name = city.getString("name");
                        String country = city.optString("country", "");
                        String admin1 = city.optString("admin1", "");

                        String displayName = name;
                        if (!admin1.isEmpty() && !admin1.equals(name)) {
                            displayName += ", " + admin1;
                        }
                        if (!country.isEmpty()) {
                            displayName += ", " + country;
                        }

                        newSuggestions.add(displayName);
                    }
                }

                runOnUiThread(() -> {
                    citySuggestions.clear();
                    citySuggestions.addAll(newSuggestions);
                    citiesAdapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void performSearch() {
        String input = searchEditText.getText().toString().trim();
        String cityName = input.split(",")[0].trim();

        if (!cityName.isEmpty()) {
            currentCity = cityName;

            // Проверяем интернет перед поиском
            if (!isNetworkAvailable()) {
                showError("Нет подключения к интернету. Используем сохраненные данные.");
                showLastSavedData();
                return;
            }

            fetchAndUpdateWeather(cityName);
            saveSelectedCity(cityName);
        }
    }

    private void saveSelectedCity(String cityName) {
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        prefs.edit().putString("last_city", cityName).apply();
    }

    private void fetchAndUpdateWeather(String cityName) {
        Log.d(TAG, "Starting weather fetch for city: " + cityName);
        new Thread(() -> {
            try {
                runOnUiThread(() -> showLoading(true));

                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name=" +
                        java.net.URLEncoder.encode(cityName, "UTF-8");
                Log.d(TAG, "Fetching geocoding data from: " + geoUrl);
                JSONObject geo = new JSONObject(httpGet(geoUrl));
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    Log.e(TAG, "No results found for city: " + cityName);
                    runOnUiThread(() -> {
                        showError("Город не найден: " + cityName);
                        showLoading(false);
                    });
                    return;
                }

                JSONObject first = results.getJSONObject(0);
                double lat = first.getDouble("latitude");
                double lon = first.getDouble("longitude");
                String timezone = first.optString("timezone", "auto");

                currentLat = lat;
                currentLon = lon;
                currentTimezone = timezone;

                // Получаем время в часовом поясе города
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", new Locale("ru"));

                // Устанавливаем часовой пояс для форматов
                TimeZone tz = TimeZone.getTimeZone(timezone);
                timeFormat.setTimeZone(tz);
                dateFormat.setTimeZone(tz);
                dayFormat.setTimeZone(tz);

                Date now = new Date();
                final String localTime = timeFormat.format(now);
                final String localDate = dateFormat.format(now);
                final String dayOfWeek = dayFormat.format(now);

                // Получаем прогноз погоды
                String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                        "&hourly=temperature_2m,relative_humidity_2m,rain,wind_speed_10m,weathercode&current=temperature_2m,weathercode,is_day&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=" + timezone + "&forecast_days=14";

                JSONObject data = new JSONObject(httpGet(forecastUrl));
                JSONObject current = data.getJSONObject("current");
                JSONObject hourly = data.getJSONObject("hourly");
                JSONObject daily = data.getJSONObject("daily");

                // Текущая погода
                final int currentTemp = (int)Math.round(current.optDouble("temperature_2m", Double.NaN));
                final int currentWeatherCode = current.optInt("weathercode", 0);
                final boolean isDay = current.optInt("is_day", 1) == 1;

                // Данные для почасовой ленты
                JSONArray times = hourly.getJSONArray("time");
                JSONArray temps = hourly.getJSONArray("temperature_2m");
                JSONArray humidity = hourly.optJSONArray("relative_humidity_2m");
                JSONArray wind = hourly.optJSONArray("wind_speed_10m");
                JSONArray weatherCodes = hourly.optJSONArray("weathercode");

                // Находим индекс текущего часа
                int startIndex = 0;
                String currentHour = localDate + "T" + localTime;
                for (int i = 0; i < times.length(); i++) {
                    if (times.getString(i).startsWith(localDate)) {
                        startIndex = i;
                        break;
                    }
                }

                List<Entry> entries = new ArrayList<>();
                final List<String> xLabels = new ArrayList<>();
                final List<Integer> hourlyWeatherCodes = new ArrayList<>();
                int count = Math.min(24, temps.length() - startIndex);

                for (int i = startIndex; i < startIndex + count; i++) {
                    if (i >= temps.length()) break;
                    double t = temps.getDouble(i);
                    entries.add(new Entry(i - startIndex, (float) t));
                    String time = times.getString(i);
                    String hour = time.substring(11, 16);
                    xLabels.add(hour);

                    if (weatherCodes != null && i < weatherCodes.length()) {
                        hourlyWeatherCodes.add(weatherCodes.getInt(i));
                    }
                }

                // Сегодняшние min/max
                String today = localDate;
                double dMin = Double.MAX_VALUE;
                double dMax = -Double.MAX_VALUE;
                JSONArray dailyTime = daily.getJSONArray("time");
                JSONArray dailyMaxTemp = daily.getJSONArray("temperature_2m_max");
                JSONArray dailyMinTemp = daily.getJSONArray("temperature_2m_min");

                for (int i = 0; i < dailyTime.length(); i++) {
                    if (dailyTime.getString(i).equals(today)) {
                        dMin = dailyMinTemp.getDouble(i);
                        dMax = dailyMaxTemp.getDouble(i);
                        break;
                    }
                }

                final String minText = String.format(Locale.getDefault(), "%.0f°", dMin);
                final String maxText = String.format(Locale.getDefault(), "%.0f°", dMax);
                final int humidityValueInt = humidity != null && humidity.length() > startIndex ?
                        humidity.getInt(startIndex) : 0;
                final double windSpeedValue = wind != null && wind.length() > startIndex ?
                        wind.getDouble(startIndex) : 0.0;
                final String humidityText = humidityValueInt + "%";
                final String windText = (int)Math.round(windSpeedValue) + " км/ч";
                final String condition = mapWeatherCodeToRu(currentWeatherCode);
                final String dateTimeText = dayOfWeek + ", " + localDate + " " + localTime;

                // Сохраняем данные в SQLite
                saveWeatherData(cityName, currentTemp, condition, dMin, dMax,
                        humidityValueInt, windSpeedValue, lat, lon, timezone, currentWeatherCode);

                runOnUiThread(() -> {
                    updateWeatherUI(cityName, currentTemp, currentWeatherCode, isDay,
                            minText, maxText, humidityText, windText, condition, dateTimeText);
                    setChart(entries, xLabels);
                    buildHourlyTimeline(xLabels, entries, hourlyWeatherCodes);
                    showLoading(false);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather data", e);
                runOnUiThread(() -> {
                    showError("Ошибка загрузки данных: " + e.getMessage());
                    showLoading(false);
                    showLastSavedData();
                });
            }
        }).start();
    }

    private void updateWeatherUI(String cityName, int currentTemp, int currentWeatherCode, boolean isDay,
                                 String minText, String maxText, String humidityText,
                                 String windText, String condition, String dateTimeText) {
        Log.d(TAG, "Updating UI for: " + cityName + ", temp: " + currentTemp);

        cityNameText.setText(cityName);
        currentTemperature.setText(currentTemp + "°");
        minTemperature.setText("Мин: " + minText);
        maxTemperature.setText("Макс: " + maxText);
        currentDateTime.setText(dateTimeText);
        humidityValue.setText(humidityText);
        windValue.setText(windText);
        weatherCondition.setText(condition);
        setWeatherAnimation(currentWeatherCode, isDay);

        Log.d(TAG, "UI updated successfully");
    }

    // Добат этот метод в класс MainActivityNew (перегрузка метода)
    private void saveWeatherData(String cityName, double temp, String condition,
                                 double min, double max, int humidity,
                                 double windSpeed, double lat, double lon,
                                 String timezone, int weatherCode) {
        if (databaseHelper != null) {
            databaseHelper.saveWeatherData(cityName, temp, condition, min, max,
                    humidity, windSpeed, lat, lon, timezone, weatherCode);
        }
    }

    private void showLastSavedData() {
        String lastCity = getLastCityFromPrefs();
        if (databaseHelper != null) {
            DatabaseHelper.WeatherData weatherData = databaseHelper.getWeatherData(lastCity);

            if (weatherData != null) {
                runOnUiThread(() -> {
                    cityNameText.setText(weatherData.city + " (кэш)");
                    currentTemperature.setText(String.format(Locale.getDefault(), "%.0f°", weatherData.temperature));
                    minTemperature.setText("Мин: " + String.format(Locale.getDefault(), "%.0f°", weatherData.minTemp));
                    maxTemperature.setText("Макс: " + String.format(Locale.getDefault(), "%.0f°", weatherData.maxTemp));
                    humidityValue.setText(weatherData.humidity + "%");
                    windValue.setText(String.format(Locale.getDefault(), "%.0f км/ч", weatherData.windSpeed));
                    weatherCondition.setText(weatherData.condition);

                    // Используем сохраненный часовой пояс и код погоды
                    if (weatherData.timezone != null && !weatherData.timezone.isEmpty()) {
                        currentTimezone = weatherData.timezone;
                    }
                    if (weatherData.latitude != 0.0 && weatherData.longitude != 0.0) {
                        currentLat = weatherData.latitude;
                        currentLon = weatherData.longitude;
                    }

                    //  Используем код погоды для точной анимации
                    boolean isDay = true; // По умолчанию день, можно определить по текущему времени
                    if (weatherData.weatherCode != 0) {
                        setWeatherAnimation(weatherData.weatherCode, isDay);
                    } else {
                        setWeatherAnimation(estimateWeatherCodeFromCondition(weatherData.condition), isDay);
                    }

                    updateCurrentDateTime(); // Обновляем время с правильным часовым поясом
                });
            } else {
                runOnUiThread(() -> {
                    showError("Нет сохраненных данных");
                });
            }
        }
    }

    // Вспомогательный метод для определения кода погоды по текстовому описанию
    private int estimateWeatherCodeFromCondition(String condition) {
        if (condition.contains("Ясно")) return 0;
        if (condition.contains("Облачно")) return 3;
        if (condition.contains("Дождь") || condition.contains("Морось")) return 61;
        if (condition.contains("Снег")) return 71;
        if (condition.contains("Гроза")) return 95;
        return 0; // по умолчанию ясно
    }

    private String getLastCityFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        return prefs.getString("last_city", "Бишкек");
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void showLoading(boolean show) {
        if (show) {
            Toast.makeText(this, "Загрузка данных...", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildHourlyTimeline(List<String> hours, List<Entry> temps, List<Integer> weatherCodes) {
        hourlyTimeline.removeAllViews();
        Context ctx = this;

        for (int i = 0; i < hours.size(); i++) {
            LinearLayout block = new LinearLayout(ctx);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(25, 20, 25, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 0, 6, 0);
            block.setLayoutParams(lp);
            block.setBackgroundResource(R.drawable.green_background);

            LottieAnimationView icon = new LottieAnimationView(ctx);
            int animRes = R.raw.clear_day;

            if (weatherCodes != null && i < weatherCodes.size()) {
                int weatherCode = weatherCodes.get(i);
                String hourStr = hours.get(i);
                int hour = Integer.parseInt(hourStr.substring(0, 2));
                boolean isNight = hour < 6 || hour >= 18;
                animRes = mapWeatherCodeToAnim(weatherCode, isNight);
            }

            icon.setAnimation(animRes);
            icon.setRepeatCount(LottieDrawable.INFINITE);
            icon.playAnimation();

            TextView tempText = new TextView(ctx);
            tempText.setTextColor(Color.WHITE);
            tempText.setText(String.format(Locale.getDefault(), "%.0f°", temps.get(i).getY()));
            tempText.setTextSize(18f);
            tempText.setGravity(Gravity.CENTER);

            TextView timeText = new TextView(ctx);
            timeText.setTextColor(Color.WHITE);
            timeText.setText(hours.get(i));
            timeText.setTextSize(14f);
            timeText.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(110, 90);
            lpIcon.gravity = Gravity.CENTER_HORIZONTAL;
            lpIcon.bottomMargin = 10;

            block.addView(icon, lpIcon);
            block.addView(tempText);
            block.addView(timeText);
            hourlyTimeline.addView(block);
        }

        if (scrollPosition > 0) {
            hourlyTimeline.post(() -> {
                hourlyTimeline.scrollTo(scrollPosition, 0);
            });
        }
    }

    private void startTimeUpdater() {
        timeUpdater = new Runnable() {
            @Override
            public void run() {
                updateCurrentDateTime();
                timeHandler.postDelayed(this, 60000);
            }
        };
        timeHandler.post(timeUpdater);
    }

    private void updateCurrentDateTime() {
        if (currentTimezone != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM, HH:mm", new Locale("ru"));
            dateFormat.setTimeZone(TimeZone.getTimeZone(currentTimezone));
            String currentTime = dateFormat.format(new Date());
            if (currentDateTime != null) {
                currentDateTime.setText(currentTime);
            }
        }
    }

    private void tryAutoSetLocation() {
        String fallback = "Бишкек";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    1002);
            searchEditText.setText(fallback);
            performSearch();
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null) {
                double lat = loc.getLatitude();
                double lon = loc.getLongitude();
                new Thread(() -> {
                    try {
                        String rev = "https://geocoding-api.open-meteo.com/v1/reverse?latitude=" + lat + "&longitude=" + lon + "&language=ru";
                        JSONObject res = new JSONObject(httpGet(rev));
                        JSONArray results = res.optJSONArray("results");
                        String name = fallback;
                        if (results != null && results.length() > 0) {
                            name = results.getJSONObject(0).optString("name", fallback);
                        }
                        String finalName = name;
                        runOnUiThread(() -> {
                            searchEditText.setText(finalName);
                            performSearch();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in reverse geocoding", e);
                        runOnUiThread(() -> {
                            searchEditText.setText(fallback);
                            performSearch();
                        });
                    }
                }).start();
            } else {
                searchEditText.setText(fallback);
                performSearch();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting location", e);
            searchEditText.setText(fallback);
            performSearch();
        }
    }

    private void setupListeners() {
        View.OnClickListener searchClickListener = v -> performSearch();

        searchIcon.setOnClickListener(searchClickListener);
        searchEditText.setOnClickListener(searchClickListener);

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                return true;
            }
            return false;
        });

        nextDaysButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NextDaysActivity.class);
            intent.putExtra("city_name", cityNameText.getText().toString());
            intent.putExtra("latitude", currentLat);
            intent.putExtra("longitude", currentLon);
            intent.putExtra("timezone", currentTimezone);
            startActivity(intent);
        });

        hourlyTimeline.getViewTreeObserver().addOnScrollChangedListener(() -> {
            scrollPosition = hourlyTimeline.getScrollX();
        });
    }

    private void setChart(List<Entry> entries, List<String> xLabels) {
        try {
            LineDataSet dataSet = new LineDataSet(entries, "");
            dataSet.setColor(Color.WHITE);
            dataSet.setValueTextColor(Color.TRANSPARENT);
            dataSet.setLineWidth(2.5f);
            dataSet.setCircleColor(Color.WHITE);
            dataSet.setCircleRadius(3f);

            LineData lineData = new LineData(dataSet);
            temperatureChart.setData(lineData);
            temperatureChart.getDescription().setEnabled(false);
            temperatureChart.getLegend().setEnabled(false);
            temperatureChart.getXAxis().setTextColor(Color.WHITE);
            temperatureChart.getAxisLeft().setTextColor(Color.WHITE);
            temperatureChart.getAxisRight().setEnabled(false);
            temperatureChart.setBackgroundColor(Color.TRANSPARENT);
            temperatureChart.getXAxis().setGranularity(1f);
            temperatureChart.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int i = (int) value;
                    if (i >= 0 && i < xLabels.size()) return xLabels.get(i);
                    return "";
                }
            });
            temperatureChart.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "Error setting chart", e);
        }
    }

    private String mapWeatherCodeToRu(int code) {
        if (code == 0) return "Ясно";
        if (code == 1 || code == 2) return "Переменная облачность";
        if (code == 3) return "Облачно";
        if (code >= 45 && code <= 48) return "Туман";
        if (code >= 51 && code <= 67) return "Морось/Дождь";
        if (code >= 71 && code <= 77) return "Снег";
        if (code >= 80 && code <= 82) return "Ливень";
        if (code >= 85 && code <= 86) return "Снегопад";
        if (code >= 95 && code <= 99) return "Гроза";
        return "Неизвестно";
    }

    private static String httpGet(String spec) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader in = null;
        try {
            conn = (HttpURLConnection) new URL(spec).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "WeatherApp/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error code: " + responseCode);
            }

            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void setWeatherAnimation(int weatherCode, boolean isDay) {
        int animationRes = R.raw.cloudy_weather; // Универсальная по умолчанию

        if (weatherCode == 0) {
            animationRes = isDay ? R.raw.clear_day : R.raw.clear_night;
        } else if (weatherCode == 1 || weatherCode == 2) {
            animationRes = isDay ? R.raw.few_clouds : R.raw.few_clouds_night;
        } else if (weatherCode == 3) {
            animationRes = R.raw.cloudy_weather; // Универсальная для облачной погоды
        } else if (weatherCode >= 45 && weatherCode <= 48) {
            animationRes = R.raw.mostly_cloudy; // Туман
        } else if (weatherCode >= 51 && weatherCode <= 67) {
            animationRes = isDay? R.raw.rainy_weather: R.raw.weather_rainy_night; // Дождь/морось
        } else if (weatherCode >= 71 && weatherCode <= 77) {
            animationRes = isDay ? R.raw.snow_weather: R.raw.weather_snow_night; // Снег
        } else if (weatherCode >= 80 && weatherCode <= 82) {
            animationRes = R.raw.shower_rain; // Ливень
        } else if (weatherCode >= 85 && weatherCode <= 86) {
            animationRes = R.raw.snow_weather; // Снегопад
        } else if (weatherCode >= 95 && weatherCode <= 99) {
            animationRes = R.raw.storm_weather; // Гроза
        }

        weatherAnimation.setAnimation(animationRes);
        weatherAnimation.playAnimation();
    }

    private int mapWeatherCodeToAnim(int weatherCode, boolean isNight) {
        // Для ночного времени используем универсальные анимации без солнца
        if (isNight) {
            switch (weatherCode) {
                case 0: // Ясно
                    return R.raw.clear_night;
                case 1: // Преимущественно ясно
                case 2: // Переменная облачность
                    return R.raw.few_clouds_night;
                case 3: // Пасмурно
                    return R.raw.cloudy_weather;
                case 45: case 48: // Туман
                    return R.raw.mostly_cloudy;
                case 51: case 53: case 55: // Морось
                case 56: case 57: // Ледяная морось
                case 61: case 63: case 65: return R.raw.weather_rainy_night; // Дождь
                case 66: case 67: // Ледяной дождь
                case 80: case 81: case 82: // Ливень
                    return R.raw.rainy_weather;
                case 71: case 73: case 75: return R.raw.weather_snow_night;// Снег
                case 77: // Снежная крупа
                case 85: case 86: // Снегопад
                    return R.raw.snow_weather;
                case 95: case 96: case 99: // Гроза
                    return R.raw.storm_weather;
                default:
                    return R.raw.cloudy_weather;
            }
        } else {
            // Для дневного времени
            switch (weatherCode) {
                case 0: // Ясно
                    return R.raw.clear_day;
                case 1: // Преимущественно ясно
                case 2: // Переменная облачность
                    return R.raw.few_clouds;
                case 3: // Пасмурно
                    return R.raw.cloudy_weather;
                case 45: case 48: // Туман
                    return R.raw.mostly_cloudy;
                case 51: case 53: case 55: // Морось
                case 56: case 57: // Ледяная морось
                case 61: case 63: case 65: // Дождь
                case 66: case 67: // Ледяной дождь
                    return R.raw.rainy_weather;
                case 80: case 81: case 82: // Ливень
                    return R.raw.shower_rain;
                case 71: case 73: case 75: // Снег
                case 77: // Снежная крупа
                case 85: case 86: // Снегопад
                    return R.raw.snow_weather;
                case 95: case 96: case 99: // Гроза
                    return R.raw.storm_weather;
                default:
                    return R.raw.cloudy_weather;
            }
        }
    }
    private void scheduleDailyNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                return;
            }
        }

        try {
            PeriodicWorkRequest notificationWork =
                    new PeriodicWorkRequest.Builder(WeatherNotificationWorker.class, 24, TimeUnit.HOURS)
                            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "daily_weather_notification",
                    ExistingPeriodicWorkPolicy.KEEP,
                    notificationWork
            );
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling notification", e);
        }
    }

    private long calculateInitialDelay() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 8);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);

        if (calendar.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        return target.getTimeInMillis() - calendar.getTimeInMillis();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scheduleDailyNotification();
            }
        } else if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted, retrying location");
                tryAutoSetLocation();
            } else {
                Log.d(TAG, "Location permission denied, using fallback city");
                searchEditText.setText("Бишкек");
                performSearch();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeUpdater != null) {
            timeHandler.removeCallbacks(timeUpdater);
        }
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}