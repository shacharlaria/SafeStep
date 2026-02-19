package com.example.safestep1;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private String userUid;

    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int MAX_LOCATIONS = 288; // 24 hours * 12 (5 min intervals)

    public static final String ACTION_START_OR_UPDATE = "ACTION_START_OR_UPDATE";
    public static final String ACTION_STOP = "ACTION_STOP";

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "Location Update: " + location.getLatitude() + ", " + location.getLongitude());
                        updateLocationHistory(location);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeStep is Active")
                .setContentText("Your location is being shared.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification);

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_OR_UPDATE.equals(action)) {
                this.userUid = intent.getStringExtra("userUid");
                startLocationUpdates(300000); // 5 minutes
            } else if (ACTION_STOP.equals(action)) {
                stopSelf();
            }
        }

        return START_STICKY;
    }

    private void startLocationUpdates(long interval) {
        fusedLocationClient.removeLocationUpdates(locationCallback);

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    private void updateLocationHistory(Location location) {
        if (userUid == null) return;

        final DocumentReference userDocRef = db.collection("users").document(userUid);
        final GeoPoint newLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(userDocRef);
            List<GeoPoint> locationPath = (List<GeoPoint>) snapshot.get("location_path");

            if (locationPath == null) {
                locationPath = new ArrayList<>();
            }

            if (locationPath.size() >= MAX_LOCATIONS) {
                locationPath.remove(0); // Remove the oldest location
            }

            locationPath.add(newLocation); // Add the new one

            transaction.update(userDocRef, "location_path", locationPath);
            return null; // Transaction success
        }).addOnSuccessListener(aVoid -> Log.d(TAG, "Location history updated successfully."))
          .addOnFailureListener(e -> Log.w(TAG, "Error updating location history.", e));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
