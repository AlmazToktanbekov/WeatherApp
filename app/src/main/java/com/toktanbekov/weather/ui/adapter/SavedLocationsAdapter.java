package com.toktanbekov.weather.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.toktanbekov.weather.R;
import com.toktanbekov.weather.model.SavedLocation;

import java.util.ArrayList;
import java.util.List;

public class SavedLocationsAdapter extends RecyclerView.Adapter<SavedLocationsAdapter.ViewHolder> {

    private List<SavedLocation> locations = new ArrayList<>();
    private OnLocationClickListener onLocationClickListener;
    private OnDeleteClickListener onDeleteClickListener;

    public interface OnLocationClickListener {
        void onLocationClick(SavedLocation location);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(SavedLocation location);
    }

    public void setOnLocationClickListener(OnLocationClickListener listener) {
        this.onLocationClickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.saved_location_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedLocation location = locations.get(position);
        holder.bind(location);
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    public void setLocations(List<SavedLocation> locations) {
        this.locations = new ArrayList<>(locations);
        notifyDataSetChanged();
    }

    public void addLocation(SavedLocation location) {
        // Проверяем, не существует ли уже такое место
        if (!locations.contains(location)) {
            locations.add(location);
            notifyItemInserted(locations.size() - 1);
        }
    }

    public void removeLocation(SavedLocation location) {
        int position = locations.indexOf(location);
        if (position >= 0) {
            locations.remove(position);
            notifyItemRemoved(position);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView locationName;
        private TextView locationCountry;
        private ImageButton deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            locationName = itemView.findViewById(R.id.location_name);
            locationCountry = itemView.findViewById(R.id.location_country);
            deleteButton = itemView.findViewById(R.id.delete_location_button);

            itemView.setOnClickListener(v -> {
                if (onLocationClickListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onLocationClickListener.onLocationClick(locations.get(position));
                    }
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (onDeleteClickListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onDeleteClickListener.onDeleteClick(locations.get(position));
                    }
                }
            });
        }

        void bind(SavedLocation location) {
            locationName.setText(location.getName());
            locationCountry.setText(location.getCountry());
        }
    }
}
