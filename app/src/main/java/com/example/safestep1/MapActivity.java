package com.example.safestep1;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private GoogleMap mMap;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private Map<String, Marker> childMarkers = new HashMap<>();
    private Map<String, Polyline> childPolylines = new HashMap<>();
    private static final String DISTRESS_CHANNEL_ID = "DistressSignalChannel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        createDistressNotificationChannel();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        findAndTrackChildren();
    }

    private void findAndTrackChildren() {
        String currentUserUid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(currentUserUid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String phone = documentSnapshot.getString("phone");
                if (phone != null) {
                    db.collection("users").whereEqualTo("parentPhone", phone).addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e);
                            return;
                        }

                        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                        boolean hasPoints = false;

                        for (DocumentSnapshot childDoc : snapshots) {
                            String childId = childDoc.getId();
                            String childName = childDoc.getString("fullname");
                            Boolean inDistress = childDoc.getBoolean("inDistress");

                            if (childDoc.contains("location_path")) {
                                List<GeoPoint> geoPoints = (List<GeoPoint>) childDoc.get("location_path");
                                if (geoPoints != null && !geoPoints.isEmpty()) {
                                    List<LatLng> path = new ArrayList<>();
                                    for (GeoPoint geoPoint : geoPoints) {
                                        path.add(new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude()));
                                    }
                                    updateMapForChild(childId, childName, path, Boolean.TRUE.equals(inDistress));

                                    LatLng latestLocation = path.get(path.size() - 1);
                                    boundsBuilder.include(latestLocation);
                                    hasPoints = true;

                                    if(Boolean.TRUE.equals(inDistress)){
                                        sendDistressNotification(childName, latestLocation);
                                    }
                                }
                            }
                        }

                        if (hasPoints) {
                            try {
                                LatLngBounds bounds = boundsBuilder.build();
                                int padding = 150; // offset from edges of the map in pixels
                                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                            } catch (IllegalStateException ex) {
                                if(childMarkers.values().iterator().hasNext()){
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(childMarkers.values().iterator().next().getPosition(), 15f));
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void updateMapForChild(String childId, String childName, List<LatLng> path, boolean inDistress) {
        if (mMap == null || path.isEmpty()) {
            return;
        }

        // Update Polyline
        Polyline polyline = childPolylines.get(childId);
        if (polyline == null) {
            PolylineOptions polylineOptions = new PolylineOptions().addAll(path).color(Color.BLUE).width(10);
            polyline = mMap.addPolyline(polylineOptions);
            childPolylines.put(childId, polyline);
        } else {
            polyline.setPoints(path);
        }

        // Update Marker
        LatLng latestLocation = path.get(path.size() - 1);
        Marker marker = childMarkers.get(childId);
        float markerColor = inDistress ? BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_AZURE;

        if (marker == null) {
            Marker newMarker = mMap.addMarker(new MarkerOptions().position(latestLocation).title(childName).icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
            childMarkers.put(childId, newMarker);
        } else {
            marker.setPosition(latestLocation);
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerColor));
        }
    }

    private void sendDistressNotification(String childName, LatLng location) {
        Intent navigationIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" + location.latitude + "," + location.longitude));
        navigationIntent.setPackage("com.google.android.apps.maps");
        PendingIntent navigationPendingIntent = PendingIntent.getActivity(this, 0, navigationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, DISTRESS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // Replace with a specific distress icon
                .setContentTitle(childName + " is in Distress!")
                .setContentText("Click to navigate to their location.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(navigationPendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createDistressNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Distress Signals";
            String description = "Notifications for when a child signals for help";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(DISTRESS_CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
