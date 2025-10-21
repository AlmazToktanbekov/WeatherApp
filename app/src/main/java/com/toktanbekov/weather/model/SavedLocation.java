package com.toktanbekov.weather.model;

import java.io.Serializable;

public class SavedLocation implements Serializable {
    private String name;
    private String country;
    private String fullName;
    private long timestamp;

    public SavedLocation() {
        this.timestamp = System.currentTimeMillis();
    }

    public SavedLocation(String name, String country) {
        this.name = name;
        this.country = country;
        this.fullName = name + ", " + country;
        this.timestamp = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        updateFullName();
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
        updateFullName();
    }

    public String getFullName() {
        return fullName;
    }

    private void updateFullName() {
        this.fullName = name + ", " + country;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SavedLocation that = (SavedLocation) obj;
        return name != null ? name.equals(that.name) : that.name == null &&
                (country != null ? country.equals(that.country) : that.country == null);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (country != null ? country.hashCode() : 0);
        return result;
    }
}
