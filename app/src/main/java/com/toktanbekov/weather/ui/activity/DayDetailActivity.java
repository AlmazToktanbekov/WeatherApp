package com.toktanbekov.weather.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.toktanbekov.weather.R;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.airbnb.lottie.LottieAnimationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DayDetailActivity extends AppCompatActivity {

    private LineChart chart;
    private TextView cityDate, dayTemp, dayMin, dayMax, dayCondition, dayHumidity, dayWind;
    private LottieAnimationView dayAnimation;
    private LinearLayout hourlyDetailTimeline;
    private String currentTimezone;

    // Вспомогательный класс для хранения данных по часам
    private static class HourData {
        String hour;
        float temp;
        int weatherCode;
        float rain;

        HourData(String hour, float temp, int weatherCode, float rain) {
            this.hour = hour;
            this.temp = temp;
            this.weatherCode = weatherCode;
            this.rain = rain;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day_detail);

        // Получаем часовой пояс из интента
        currentTimezone = getIntent().getStringExtra("timezone");
        if (currentTimezone == null) {
            currentTimezone = "Asia/Bishkek";
        }

        // Инициализация всех View
        initViews();

        String city = getIntent().getStringExtra("city_name");
        String date = getIntent().getStringExtra("date");


        if (city == null) city = "Бишкек";
        if (date == null) {
            Toast.makeText(this, "Дата не указана", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.back_button).setOnClickListener(v -> onBackPressed());

        // Показываем переданные детали сразу
        showInitialData(city, date);

        // Загружаем детальные данные
        loadDetailedWeatherData(city, date);
    }

    private void initViews() {
        chart = findViewById(R.id.day_detail_chart);
        cityDate = findViewById(R.id.city_date);
        dayTemp = findViewById(R.id.day_temp);
        dayMin = findViewById(R.id.day_min);
        dayMax = findViewById(R.id.day_max);
        dayCondition = findViewById(R.id.day_condition);
        dayHumidity = findViewById(R.id.day_humidity);
        dayWind = findViewById(R.id.day_wind);
        dayAnimation = findViewById(R.id.day_animation);
        hourlyDetailTimeline = findViewById(R.id.hourly_detail_timeline);
    }

    private void showInitialData(String city, String date) {
        int minPassed = getIntent().getIntExtra("min", Integer.MIN_VALUE);
        int maxPassed = getIntent().getIntExtra("max", Integer.MIN_VALUE);
        int humPassed = getIntent().getIntExtra("humidity", Integer.MIN_VALUE);
        int windPassed = getIntent().getIntExtra("wind", Integer.MIN_VALUE);
        int codePassed = getIntent().getIntExtra("weathercode", -1);

        String headerInit = city + ", " + date;
        cityDate.setText(headerInit);

        if (minPassed != Integer.MIN_VALUE)
            dayMin.setText("Мин: " + String.format(java.util.Locale.getDefault(), "%d°", minPassed));
        if (maxPassed != Integer.MIN_VALUE)
            dayMax.setText("Макс: " + String.format(java.util.Locale.getDefault(), "%d°", maxPassed));if (humPassed != Integer.MIN_VALUE)
            dayHumidity.setText(humPassed + "%");
        if (windPassed != Integer.MIN_VALUE)
            dayWind.setText(windPassed + " км/ч");
        if (codePassed >= 0) {
            String condInit = mapWeatherCodeToRu(codePassed);
            dayCondition.setText(condInit);
            // Используем дневную анимацию по умолчанию для общего отображения дня
            setAnimation(codePassed, false);
        }
    }

    private void loadDetailedWeatherData(final String city, final String date) {
        new Thread(() -> {
            try {
                // Шаг 1: Получаем координаты города
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name=" +
                        java.net.URLEncoder.encode(city, "UTF-8");
                JSONObject geo = new JSONObject(httpGet(geoUrl));
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    runOnUiThread(() ->
                            Toast.makeText(DayDetailActivity.this, "Город не найден", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                JSONObject first = results.getJSONObject(0);
                double lat = first.getDouble("latitude");
                double lon = first.getDouble("longitude");
                String timezone = first.optString("timezone", "auto");
                currentTimezone = timezone;

                // Шаг 2: Получаем детальные данные погоды
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                        "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,rain,weathercode" +
                        "&daily=weathercode,temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto&forecast_days=14";

                JSONObject data = new JSONObject(httpGet(url));
                processWeatherData(data, city, date, lat, lon);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(DayDetailActivity.this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    
    private void processWeatherData(JSONObject data, String city, String date, double lat, double lon) throws Exception {
        JSONObject hourly = data.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray humidity = hourly.optJSONArray("relative_humidity_2m");
        JSONArray wind = hourly.optJSONArray("wind_speed_10m");
        JSONArray rain = hourly.optJSONArray("rain");
        JSONArray weatherCodes = hourly.optJSONArray("weathercode");

        // Получаем daily данные для правильного определения погоды
        JSONObject daily = data.getJSONObject("daily");
        JSONArray dailyTime = daily.getJSONArray("time");
        JSONArray dailyWeatherCode = daily.getJSONArray("weathercode");
        JSONArray dailyMaxTemp = daily.getJSONArray("temperature_2m_max");
        JSONArray dailyMinTemp = daily.getJSONArray("temperature_2m_min");

        List<Entry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        // Находим индекс дня в daily данных
        int dailyIndex = -1;
        for (int i = 0; i < dailyTime.length(); i++) {
            if (dailyTime.getString(i).equals(date)) {
                dailyIndex = i;
                break;
            }
        }

        // Собираем все часы для выбранного дня
        List<HourData> dayHours = new ArrayList<>();
        for (int i = 0; i < times.length(); i++) {
            String t = times.getString(i);
            if (t.startsWith(date)) {
                float temp = (float) temps.getDouble(i);
                String hour = t.substring(11, 16);
                int weatherCode = (weatherCodes != null && i < weatherCodes.length()) ?
                        weatherCodes.optInt(i, 0) : 0;
                float rainAmount = (rain != null && i < rain.length()) ?
                        (float) rain.optDouble(i, 0.0) : 0.0f;
                dayHours.add(new HourData(hour, temp, weatherCode, rainAmount));
            }
        }

        // Сортируем по времени (00:00 - 23:00)
        Collections.sort(dayHours, new Comparator<HourData>() {
            @Override
            public int compare(HourData h1, HourData h2) {
                return h1.hour.compareTo(h2.hour);
            }
        });

        // Создаем записи для графика и метки
        for (int i = 0; i < dayHours.size(); i++) {
            HourData hour = dayHours.get(i);
            entries.add(new Entry(i, hour.temp));
            xLabels.add(hour.hour);
        }

        // Используем daily данные для температуры и погодных условий
        final double dailyMin = (dailyIndex != -1) ? dailyMinTemp.getDouble(dailyIndex) : 0;
        final double dailyMax = (dailyIndex != -1) ? dailyMaxTemp.getDouble(dailyIndex) : 0;
        final int weatherCode = (dailyIndex != -1) ? dailyWeatherCode.getInt(dailyIndex) : -1;

        // Расчет средней температуры для дня
        final double avgTemp = (dailyMin + dailyMax) / 2;

        final String minText = String.format(java.util.Locale.getDefault(), "%.0f°", dailyMin);
        final String maxText = String.format(java.util.Locale.getDefault(), "%.0f°", dailyMax);
        final String avgText = String.format(java.util.Locale.getDefault(), "%.0f°", avgTemp);

        // Получаем данные о влажности и ветре (берем первое значение дня)
        final String humText = (dayHours.size() > 0 && humidity != null && humidity.length() > 0) ?
                humidity.optInt(0, 0) + "%" : "--%";
        final String windText = (dayHours.size() > 0 && wind != null && wind.length() > 0) ?
                (int)Math.round(wind.optDouble(0, 0.0)) + " км/ч" : "-- км/ч";

        // Используем правильное определение погоды по коду
        final String cond = (weatherCode != -1) ? mapWeatherCodeToRu(weatherCode) : "Неизвестно";

        // Форматируем заголовок
        final String header = city + ", " + formatDate(date);

        // Обновляем UI в главном потоке
        new Handler(Looper.getMainLooper()).post(() -> {
            cityDate.setText(header);
            dayMin.setText("Мин: " + minText);        // ИСПР
            dayMax.setText("Макс: " + maxText);
            dayTemp.setText(avgText);
            dayHumidity.setText(humText);
            dayWind.setText(windText);
            dayCondition.setText(cond);
            // Используем дневную анимацию по умолчанию для общего отображения дня
            setAnimation(weatherCode, false);
            setChart(entries, xLabels);
            buildHourlyDetailTimeline(dayHours);
        });
    }

    private String formatDate(String dateStr) {
        try {
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd.MM.yyyy", new java.util.Locale("ru"));
            java.util.Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private void setChart(List<Entry> entries, List<String> labels) {
        try {
            if (entries.isEmpty()) return;

            LineDataSet dataSet = new LineDataSet(entries, "");
            dataSet.setColor(android.graphics.Color.WHITE);
            dataSet.setValueTextColor(android.graphics.Color.TRANSPARENT);
            dataSet.setLineWidth(2.5f);
            dataSet.setCircleColor(android.graphics.Color.WHITE);
            dataSet.setCircleRadius(3f);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.getDescription().setEnabled(false);
            chart.getLegend().setEnabled(false);
            chart.getXAxis().setTextColor(android.graphics.Color.WHITE);
            chart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
            chart.getAxisRight().setEnabled(false);
            chart.getXAxis().setGranularity(1f);
            chart.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int i = (int) value;
                    if (i >= 0 && i < labels.size()) return labels.get(i);
                    return "";
                }
            });
            chart.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
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

    private void buildHourlyDetailTimeline(List<HourData> dayHours) {
        hourlyDetailTimeline.removeAllViews();
        if (dayHours.isEmpty()) return;

        // Получаем текущий час города для выделения
        java.util.Calendar now = getCityCalendar();
        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        String currentTimeStr = String.format("%02d:00", currentHour);

        for (int i = 0; i < dayHours.size(); i++) {
            HourData hourData = dayHours.get(i);

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(25, 20, 25, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 0, 6, 0);
            block.setLayoutParams(lp);


            block.setBackgroundResource(R.drawable.glass_background);

            // Weather icon based on weather code and time
            LottieAnimationView icon = new LottieAnimationView(this);
            int hour = Integer.parseInt(hourData.hour.substring(0, 2));
            boolean isNight = hour < 6 || hour >= 18;
            int animRes = mapWeatherCodeToAnim(hourData.weatherCode, isNight);

            icon.setAnimation(animRes);
            icon.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
            icon.playAnimation();

            // Temperature
            TextView tempText = new TextView(this);
            tempText.setTextColor(android.graphics.Color.WHITE);
            tempText.setText(String.format(java.util.Locale.getDefault(), "%.0f°", hourData.temp));
            tempText.setTextSize(18f);
            tempText.setGravity(android.view.Gravity.CENTER);

            // Time
            TextView timeText = new TextView(this);
            timeText.setTextColor(android.graphics.Color.WHITE);
            timeText.setText(hourData.hour);
            timeText.setTextSize(14f);
            timeText.setGravity(android.view.Gravity.CENTER);

            // Layout params for icon
            LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(110, 90);
            lpIcon.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            lpIcon.bottomMargin = 10;

            block.addView(icon, lpIcon);
            block.addView(tempText);
            block.addView(timeText);
            hourlyDetailTimeline.addView(block);
        }
    }
    private void setAnimation(int weatherCode, boolean isNight) {
        int animRes = mapWeatherCodeToAnim(weatherCode, isNight);
        dayAnimation.setAnimation(animRes);
        dayAnimation.playAnimation();
    }

    private int mapWeatherCodeToAnim(int weatherCode, boolean isNight) {
        // Универсальные анимации для большинства случаев
        switch (weatherCode) {
            case 0:  return isNight ? R.raw.clear_night : R.raw.clear_day; // Ясно

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
                return isNight ? R.raw.weather_rainy_night :R.raw.rainy_weather;
            case 71: case 73: case 75: // Снег
            case 77: // Снежная крупа
            case 85: case 86: // Снегопад
                return isNight ?R.raw.weather_snow_night : R.raw.snow_weather;
            case 95: case 96: case 99: // Гроза
                return R.raw.storm_weather;
            default:
                return R.raw.cloudy_weather;
        }
    }

    private java.util.Calendar getCityCalendar() {
        try {
            java.util.TimeZone tz = java.util.TimeZone.getTimeZone(currentTimezone);
            return java.util.Calendar.getInstance(tz);
        } catch (Exception e) {
            e.printStackTrace();
            return java.util.Calendar.getInstance();
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
}