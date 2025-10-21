package com.toktanbekov.weather.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "weather.db";
    private static final int DATABASE_VERSION = 3; // Увеличиваем версию

    // Таблица для сохранения погоды
    private static final String TABLE_WEATHER = "weather_data";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CITY = "city_name";
    private static final String COLUMN_TEMPERATURE = "temperature";
    private static final String COLUMN_CONDITION = "weather_condition";
    private static final String COLUMN_MIN_TEMP = "min_temp";
    private static final String COLUMN_MAX_TEMP = "max_temp";
    private static final String COLUMN_HUMIDITY = "humidity";
    private static final String COLUMN_WIND_SPEED = "wind_speed";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_JSON_DATA = "json_data";
    private static final String COLUMN_TIMEZONE = "timezone"; // НОВАЯ колонка
    private static final String COLUMN_WEATHER_CODE = "weather_code"; // НОВАЯ колонка

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_WEATHER_TABLE = "CREATE TABLE " + TABLE_WEATHER + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_CITY + " TEXT,"
                + COLUMN_TEMPERATURE + " REAL,"
                + COLUMN_CONDITION + " TEXT,"
                + COLUMN_MIN_TEMP + " REAL,"
                + COLUMN_MAX_TEMP + " REAL,"
                + COLUMN_HUMIDITY + " INTEGER,"
                + COLUMN_WIND_SPEED + " REAL,"
                + COLUMN_LATITUDE + " REAL,"
                + COLUMN_LONGITUDE + " REAL,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_JSON_DATA + " TEXT,"
                + COLUMN_TIMEZONE + " TEXT," // НОВАЯ колонка
                + COLUMN_WEATHER_CODE + " INTEGER" // НОВАЯ колонка
                + ")";
        db.execSQL(CREATE_WEATHER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Добавляем колонку json_data для версии 2
            db.execSQL("ALTER TABLE " + TABLE_WEATHER + " ADD COLUMN " + COLUMN_JSON_DATA + " TEXT");
        }
        if (oldVersion < 3) {
            // Добавляем новые колонки для версии 3
            db.execSQL("ALTER TABLE " + TABLE_WEATHER + " ADD COLUMN " + COLUMN_TIMEZONE + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_WEATHER + " ADD COLUMN " + COLUMN_WEATHER_CODE + " INTEGER");
        }
    }

    // Сохранение данных о погоде (основной метод)
    public void saveWeatherData(String city, double temp, String condition,
                                double minTemp, double maxTemp, int humidity,
                                double windSpeed, double lat, double lon) {
        saveWeatherData(city, temp, condition, minTemp, maxTemp, humidity, windSpeed, lat, lon, null, 0);
    }

    // Сохранение данных о погоде с часовым поясом и кодом погоды
    public void saveWeatherData(String city, double temp, String condition,
                                double minTemp, double maxTemp, int humidity,
                                double windSpeed, double lat, double lon,
                                String timezone, int weatherCode) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_CITY, city);
        values.put(COLUMN_TEMPERATURE, temp);
        values.put(COLUMN_CONDITION, condition);
        values.put(COLUMN_MIN_TEMP, minTemp);
        values.put(COLUMN_MAX_TEMP, maxTemp);
        values.put(COLUMN_HUMIDITY, humidity);
        values.put(COLUMN_WIND_SPEED, windSpeed);
        values.put(COLUMN_LATITUDE, lat);
        values.put(COLUMN_LONGITUDE, lon);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());

        // Новые поля
        if (timezone != null) {
            values.put(COLUMN_TIMEZONE, timezone);
        }
        if (weatherCode > 0) {
            values.put(COLUMN_WEATHER_CODE, weatherCode);
        }

        // Удаляем старые данные для этого города
        db.delete(TABLE_WEATHER, COLUMN_CITY + " = ?", new String[]{city});

        // Вставляем новые данные
        db.insert(TABLE_WEATHER, null, values);
        db.close();
    }

    // Получение сохраненных данных о погоде для города
    public WeatherData getWeatherData(String city) {
        SQLiteDatabase db = this.getReadableDatabase();
        WeatherData weatherData = null;

        Cursor cursor = db.query(TABLE_WEATHER, null, COLUMN_CITY + " = ?",
                new String[]{city}, null, null, COLUMN_TIMESTAMP + " DESC", "1");

        if (cursor != null && cursor.moveToFirst()) {
            weatherData = new WeatherData();

            // Безопасное получение данных из курсора
            int cityIndex = cursor.getColumnIndex(COLUMN_CITY);
            int tempIndex = cursor.getColumnIndex(COLUMN_TEMPERATURE);
            int conditionIndex = cursor.getColumnIndex(COLUMN_CONDITION);
            int minTempIndex = cursor.getColumnIndex(COLUMN_MIN_TEMP);
            int maxTempIndex = cursor.getColumnIndex(COLUMN_MAX_TEMP);
            int humidityIndex = cursor.getColumnIndex(COLUMN_HUMIDITY);
            int windSpeedIndex = cursor.getColumnIndex(COLUMN_WIND_SPEED);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int lonIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);
            int timezoneIndex = cursor.getColumnIndex(COLUMN_TIMEZONE); // НОВОЕ
            int weatherCodeIndex = cursor.getColumnIndex(COLUMN_WEATHER_CODE); // НОВОЕ

            if (cityIndex >= 0) weatherData.city = cursor.getString(cityIndex);
            if (tempIndex >= 0) weatherData.temperature = cursor.getDouble(tempIndex);
            if (conditionIndex >= 0) weatherData.condition = cursor.getString(conditionIndex);
            if (minTempIndex >= 0) weatherData.minTemp = cursor.getDouble(minTempIndex);
            if (maxTempIndex >= 0) weatherData.maxTemp = cursor.getDouble(maxTempIndex);
            if (humidityIndex >= 0) weatherData.humidity = cursor.getInt(humidityIndex);
            if (windSpeedIndex >= 0) weatherData.windSpeed = cursor.getDouble(windSpeedIndex);
            if (latIndex >= 0) weatherData.latitude = cursor.getDouble(latIndex);
            if (lonIndex >= 0) weatherData.longitude = cursor.getDouble(lonIndex);

            // Новые поля
            if (timezoneIndex >= 0) weatherData.timezone = cursor.getString(timezoneIndex);
            if (weatherCodeIndex >= 0) weatherData.weatherCode = cursor.getInt(weatherCodeIndex);

            cursor.close();
        }
        db.close();
        return weatherData;
    }

    // Получение списка всех сохраненных городов
    public List<String> getSavedCities() {
        List<String> cities = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(true, TABLE_WEATHER, new String[]{COLUMN_CITY},
                null, null, null, null, COLUMN_TIMESTAMP + " DESC", null);

        if (cursor != null) {
            int cityIndex = cursor.getColumnIndex(COLUMN_CITY);
            if (cityIndex >= 0) {
                while (cursor.moveToNext()) {
                    cities.add(cursor.getString(cityIndex));
                }
            }
            cursor.close();
        }
        db.close();
        return cities;
    }

    // Сохранение полных JSON данных
    public void saveWeatherData(String cityName, int temperature, int weatherCode, String jsonData) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_CITY, cityName);
        values.put(COLUMN_TEMPERATURE, temperature);
        values.put(COLUMN_CONDITION, mapWeatherCodeToString(weatherCode));
        values.put(COLUMN_WEATHER_CODE, weatherCode); // Сохраняем код погоды
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_JSON_DATA, jsonData);

        // Удаляем старые данные для этого города
        db.delete(TABLE_WEATHER, COLUMN_CITY + " = ?", new String[]{cityName});

        // Вставляем новые данные
        db.insert(TABLE_WEATHER, null, values);
        db.close();
    }

    // Получение последних JSON данных для города
    public String getLastWeatherData(String cityName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String jsonData = null;

        Cursor cursor = db.query(TABLE_WEATHER, new String[]{COLUMN_JSON_DATA},
                COLUMN_CITY + " = ?", new String[]{cityName},
                null, null, COLUMN_TIMESTAMP + " DESC", "1");

        if (cursor != null && cursor.moveToFirst()) {
            int jsonIndex = cursor.getColumnIndex(COLUMN_JSON_DATA);
            if (jsonIndex >= 0) {
                jsonData = cursor.getString(jsonIndex);
            }
            cursor.close();
        }
        db.close();
        return jsonData;
    }

    // НОВЫЙ МЕТОД: Получение последнего сохраненного города
    public String getLastSavedCity() {
        SQLiteDatabase db = this.getReadableDatabase();
        String lastCity = null;

        Cursor cursor = db.query(TABLE_WEATHER,
                new String[]{COLUMN_CITY},
                null, null, null, null,
                COLUMN_TIMESTAMP + " DESC", "1");

        if (cursor != null && cursor.moveToFirst()) {
            int cityIndex = cursor.getColumnIndex(COLUMN_CITY);
            if (cityIndex >= 0) {
                lastCity = cursor.getString(cityIndex);
            }
            cursor.close();
        }
        db.close();
        return lastCity;
    }

    // НОВЫЙ МЕТОД: Удаление старых данных (старше 7 дней)
    public void cleanupOldData() {
        SQLiteDatabase db = this.getWritableDatabase();
        long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);

        db.delete(TABLE_WEATHER, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(oneWeekAgo)});
        db.close();
    }

    // НОВЫЙ МЕТОД: Проверка существования данных для города
    public boolean hasWeatherData(String city) {
        SQLiteDatabase db = this.getReadableDatabase();
        boolean exists = false;

        Cursor cursor = db.query(TABLE_WEATHER,
                new String[]{COLUMN_ID},
                COLUMN_CITY + " = ?",
                new String[]{city},
                null, null, null, "1");

        if (cursor != null) {
            exists = cursor.getCount() > 0;
            cursor.close();
        }
        db.close();
        return exists;
    }

    private String mapWeatherCodeToString(int code) {
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

    // ОБНОВЛЕННЫЙ класс для хранения данных о погоде
    public static class WeatherData {
        public String city = "";
        public double temperature = 0.0;
        public String condition = "";
        public double minTemp = 0.0;
        public double maxTemp = 0.0;
        public int humidity = 0;
        public double windSpeed = 0.0;
        public double latitude = 0.0;
        public double longitude = 0.0;
        public String timezone = "Asia/Bishkek"; // Значение по умолчанию
        public int weatherCode = 0; // Код погоды для точной анимации
    }
}