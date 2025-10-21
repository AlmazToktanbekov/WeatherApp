package com.toktanbekov.weather.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.toktanbekov.weather.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NextDaysActivity extends AppCompatActivity {

    private LinearLayout daysContainer;
    private LineChart daysChart;
    private String currentTimezone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_next_days);

        daysContainer = findViewById(R.id.days_container);
        daysChart = findViewById(R.id.days_chart);

        // Получаем часовой пояс из интента
        currentTimezone = getIntent().getStringExtra("timezone");
        if (currentTimezone == null) {
            currentTimezone = "Asia/Bishkek";
        }

        // Setup back button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        // Load and display forecast data
        String city = getIntent().getStringExtra("city_name");
        if (city == null) city = "Бишкек";
        loadForecastData(city);
    }

    private void loadForecastData(String cityName) {
        new Thread(() -> {
            try {
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name=" +
                        java.net.URLEncoder.encode(cityName, "UTF-8");
                JSONObject geo = new JSONObject(httpGet(geoUrl));
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    runOnUiThread(() -> Toast.makeText(NextDaysActivity.this, "Город не найден", Toast.LENGTH_SHORT).show());
                    return;
                }
                JSONObject first = results.getJSONObject(0);
                double lat = first.getDouble("latitude");
                double lon = first.getDouble("longitude");
                String timezone = first.optString("timezone", "auto");
                currentTimezone = timezone;

                String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                        "&daily=weathercode,temperature_2m_max,temperature_2m_min,relative_humidity_2m_max,wind_speed_10m_max&timezone=auto&forecast_days=14";
                JSONObject data = new JSONObject(httpGet(forecastUrl));
                JSONObject daily = data.getJSONObject("daily");

                // Безопасное получение JSON массивов
                JSONArray dailyTime = daily.optJSONArray("time");
                JSONArray dailyMaxTemp = daily.optJSONArray("temperature_2m_max");
                JSONArray dailyMinTemp = daily.optJSONArray("temperature_2m_min");
                JSONArray dailyWeatherCode = daily.optJSONArray("weathercode");
                JSONArray dailyHumidity = daily.optJSONArray("relative_humidity_2m_max");
                JSONArray dailyWind = daily.optJSONArray("wind_speed_10m_max");

                // Проверка на null массивы
                if (dailyTime == null || dailyMaxTemp == null || dailyMinTemp == null || dailyWeatherCode == null) {
                    runOnUiThread(() -> Toast.makeText(NextDaysActivity.this, "Ошибка получения данных", Toast.LENGTH_SHORT).show());
                    return;
                }

                processForecastData(cityName, dailyTime, dailyMaxTemp, dailyMinTemp, dailyWeatherCode, dailyHumidity, dailyWind);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(NextDaysActivity.this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void processForecastData(String cityName, JSONArray dailyTime, JSONArray dailyMaxTemp,
                                     JSONArray dailyMinTemp, JSONArray dailyWeatherCode,
                                     JSONArray dailyHumidity, JSONArray dailyWind) {
        // Данные для графика
        List<Entry> maxEntries = new ArrayList<>();
        List<Entry> minEntries = new ArrayList<>();
        List<String> chartLabels = new ArrayList<>();

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());

        for (int i = 0; i < dailyTime.length(); i++) {
            try {
                String dateStr = dailyTime.getString(i);
                float maxTemp = (float) dailyMaxTemp.getDouble(i);
                float minTemp = (float) dailyMinTemp.getDouble(i);

                maxEntries.add(new Entry(i, maxTemp));
                minEntries.add(new Entry(i, minTemp));

                try {
                    Date date = inputFormat.parse(dateStr);
                    chartLabels.add(outputFormat.format(date));
                } catch (Exception e) {
                    chartLabels.add(dateStr);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
        }

        String finalCityName = cityName;
        runOnUiThread(() -> {
            // Устанавливаем график только если есть данные
            if (!maxEntries.isEmpty() && !minEntries.isEmpty()) {
                setChart(maxEntries, minEntries, chartLabels);
            }

            displayDaysForecast(finalCityName, dailyTime, dailyMaxTemp, dailyMinTemp, dailyWeatherCode, dailyHumidity, dailyWind);
        });
    }

    private void displayDaysForecast(String cityName, JSONArray dailyTime, JSONArray dailyMaxTemp,
                                     JSONArray dailyMinTemp, JSONArray dailyWeatherCode,
                                     JSONArray dailyHumidity, JSONArray dailyWind) {
        daysContainer.removeAllViews();
        int added = 0;
        boolean skipToday = true;
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat out = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        for (int i = 0; i < dailyTime.length(); i++) {
            if (added >= 14) break;

            try {
                String dayKey = dailyTime.getString(i);
                double maxTemp = dailyMaxTemp.getDouble(i);
                double minTemp = dailyMinTemp.getDouble(i);
                int weatherCode = dailyWeatherCode.getInt(i);

                // Пропускаем сегодняшний день
                if (skipToday) {
                    skipToday = false;
                    continue;
                }

                // Расчет средней температуры для каждого дня
                double avgTemp = (maxTemp + minTemp) / 2;

                // Получаем данные о влажности и ветре
                double humidity = (dailyHumidity != null && i < dailyHumidity.length()) ? dailyHumidity.getDouble(i) : 0.0;
                double windSpeed = (dailyWind != null && i < dailyWind.length()) ? dailyWind.getDouble(i) : 0.0;

                View dayView = createDayView(cityName, dayKey, avgTemp, maxTemp, minTemp, weatherCode, humidity, windSpeed, in, out);
                daysContainer.addView(dayView);
                added++;

            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
        }

        // Если не добавили ни одного дня, показываем сообщение
        if (added == 0) {
            Toast.makeText(NextDaysActivity.this, "Нет данных для отображения", Toast.LENGTH_SHORT).show();
        }
    }

    private View createDayView(String cityName, String dayKey, double avgTemp, double maxTemp,
                               double minTemp, int weatherCode, double humidity, double windSpeed,
                               SimpleDateFormat in, SimpleDateFormat out) {
        View dayView = LayoutInflater.from(this).inflate(R.layout.day_forecast_item, daysContainer, false);

        ((TextView) dayView.findViewById(R.id.day_temperature)).setText(String.format(Locale.getDefault(), "%.0f°", avgTemp));

        String dateLabel;
        try {
            dateLabel = out.format(in.parse(dayKey));
        } catch (Exception ex) {
            dateLabel = dayKey;
        }

        String week;
        try {
            week = new SimpleDateFormat("EE", new Locale("ru")).format(in.parse(dayKey));
        } catch (Exception ex) {
            week = "?";
        }

        ((TextView) dayView.findViewById(R.id.day_city_date)).setText(cityName + ", " + week + " " + dateLabel);
        ((TextView) dayView.findViewById(R.id.day_humidity)).setText("Макс: " + String.format(Locale.getDefault(), "%.0f°", maxTemp));
        ((TextView) dayView.findViewById(R.id.day_wind)).setText("Мин: " + String.format(Locale.getDefault(), "%.0f°", minTemp));

        String cond = mapWeatherCodeToRu(weatherCode);
        ((TextView) dayView.findViewById(R.id.day_weather_condition)).setText(cond);

        LottieAnimationView anim = dayView.findViewById(R.id.day_weather_animation);
        // Используем дневную анимацию для общего отображения дня
        anim.setAnimation(mapWeatherCodeToAnim(weatherCode, false));
        anim.playAnimation();

        // Устанавливаем обработчик клика
        setupDayClickListener(dayView, cityName, dayKey, minTemp, maxTemp, humidity, windSpeed, weatherCode);

        return dayView;
    }

    private void setupDayClickListener(View dayView, String cityName, String date,
                                       double minTemp, double maxTemp, double humidity,
                                       double windSpeed, int weatherCode) {
        dayView.setOnClickListener(v -> {
            Intent intent = new Intent(this, DayDetailActivity.class);
            intent.putExtra("city_name", cityName);
            intent.putExtra("date", date);
            intent.putExtra("min", (int) Math.round(minTemp));
            intent.putExtra("max", (int) Math.round(maxTemp));
            intent.putExtra("humidity", (int) Math.round(humidity));
            intent.putExtra("wind", (int) Math.round(windSpeed));
            intent.putExtra("weathercode", weatherCode);
            intent.putExtra("timezone", currentTimezone); // Передаем часовой пояс
            startActivity(intent);
        });
    }

    private void setChart(List<Entry> maxEntries, List<Entry> minEntries, List<String> labels) {
        try {
            // Настройка линии для максимальной температуры
            LineDataSet maxDataSet = new LineDataSet(maxEntries, "Максимальная температура");
            maxDataSet.setColor(android.graphics.Color.RED);
            maxDataSet.setValueTextColor(android.graphics.Color.TRANSPARENT);
            maxDataSet.setLineWidth(2.5f);
            maxDataSet.setCircleColor(android.graphics.Color.RED);
            maxDataSet.setCircleRadius(3f);

            // Настройка линии для минимальной температуры
            LineDataSet minDataSet = new LineDataSet(minEntries, "Минимальная температура");
            minDataSet.setColor(android.graphics.Color.BLUE);
            minDataSet.setValueTextColor(android.graphics.Color.TRANSPARENT);
            minDataSet.setLineWidth(2.5f);
            minDataSet.setCircleColor(android.graphics.Color.BLUE);
            minDataSet.setCircleRadius(3f);

            LineData lineData = new LineData(maxDataSet, minDataSet);
            daysChart.setData(lineData);

            // Настройка внешнего вида графика
            daysChart.getDescription().setEnabled(false);
            daysChart.getLegend().setEnabled(false);
            daysChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
            daysChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
            daysChart.getAxisRight().setEnabled(false);
            daysChart.getXAxis().setGranularity(1f);
            daysChart.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            // Форматирование осей X
            daysChart.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int i = (int) value;
                    if (i >= 0 && i < labels.size()) return labels.get(i);
                    return "";
                }
            });

            daysChart.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error code: " + responseCode + " for URL: " + spec);
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

    private String mapWeatherCodeToRu(int code) {
        if (code == 0) return "Ясно";
        if (code == 1) return "Преимущественно ясно";
        if (code == 2) return "Переменная облачность";
        if (code == 3) return "Пасмурно";
        if (code >= 45 && code <= 48) return "Туман";
        if (code >= 51 && code <= 55) return "Морось";
        if (code >= 56 && code <= 57) return "Ледяная морось";
        if (code >= 61 && code <= 65) return "Дождь";
        if (code >= 66 && code <= 67) return "Ледяной дождь";
        if (code >= 71 && code <= 77) return "Снег";
        if (code >= 80 && code <= 82) return "Ливень";
        if (code >= 85 && code <= 86) return "Снегопад";
        if (code >= 95 && code <= 99) return "Гроза";
        return "Неизвестно";
    }

    private int mapWeatherCodeToAnim(int weatherCode, boolean isNight) {
        // Универсальные анимации для большинства случаев
        switch (weatherCode) {
            case 0: // Ясно
                return isNight ? R.raw.clear_night : R.raw.clear_day;
            case 1: // Преимущественно ясно
            case 2: // Переменная облачность
                return isNight ? R.raw.few_clouds_night : R.raw.few_clouds;
            case 3: // Пасмурно
                return R.raw.cloudy_weather;
            case 45: case 48: // Туман
                return R.raw.mostly_cloudy;
            case 51: case 53: case 55: // Морось
            case 56: case 57: // Ледяная морось
            case 61: case 63: case 65: // Дождь
            case 66: case 67: // Ледяной дождь
            case 80: case 81: case 82: // Ливень
                return R.raw.rainy_weather;
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