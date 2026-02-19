package com.example.safestep1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private LinearLayout loggedOutGroup, parentButtons, childButtons;
    private ConstraintLayout loggedInGroup;
    private Button loginButton, registerButton, logoutButton, mapButton, parentChatButton, helpButton, childChatButton, startTrackingButton;
    private TextView welcomeText;
    private ImageView profileImage;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final int PERMISSIONS_REQUEST_CODE = 1002;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1003;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1004;
    private static final int SEND_SMS_PERMISSION_REQUEST_CODE = 1005;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        loggedOutGroup = findViewById(R.id.logged_out_group);
        loggedInGroup = findViewById(R.id.logged_in_group);
        parentButtons = findViewById(R.id.parent_buttons);
        childButtons = findViewById(R.id.child_buttons);
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        logoutButton = findViewById(R.id.logout_button);
        mapButton = findViewById(R.id.map_button);
        parentChatButton = findViewById(R.id.parent_chat_button);
        helpButton = findViewById(R.id.help_button);
        childChatButton = findViewById(R.id.child_chat_button);
        startTrackingButton = findViewById(R.id.start_tracking_button);
        welcomeText = findViewById(R.id.welcome_text);
        profileImage = findViewById(R.id.profile_image);

        // Set OnClick Listeners
        loginButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LogIn.class)));
        registerButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Register.class)));
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            stopLocationService();
            updateUI(null);
        });

        mapButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, MapActivity.class)));
        startTrackingButton.setOnClickListener(v -> checkAndRequestPermissions());
        helpButton.setOnClickListener(v -> handleHelpButtonClick());

    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            loggedInGroup.setVisibility(View.VISIBLE);
            loggedOutGroup.setVisibility(View.GONE);
            fetchUserDetails(user.getUid());
        } else {
            loggedInGroup.setVisibility(View.GONE);
            loggedOutGroup.setVisibility(View.VISIBLE);
        }
    }

    private void fetchUserDetails(String uid) {
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                String name = snapshot.getString("fullname");
                String userType = snapshot.getString("userType");
                String imageUrl = snapshot.getString("imageURL");

                welcomeText.setText("Hello, " + name);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(this).load(imageUrl).into(profileImage);
                }

                if ("PARENT".equals(userType)) {
                    parentButtons.setVisibility(View.VISIBLE);
                    childButtons.setVisibility(View.GONE);
                } else if ("CHILD".equals(userType)) {
                    childButtons.setVisibility(View.VISIBLE);
                    parentButtons.setVisibility(View.GONE);
                    // Update help button based on distress state
                    Boolean inDistress = snapshot.getBoolean("inDistress");
                    if (Boolean.TRUE.equals(inDistress)) {
                        helpButton.setText("I'm Safe");
                        helpButton.setBackgroundColor(Color.GREEN);
                    } else {
                        helpButton.setText("Signal for Help");
                        helpButton.setBackgroundColor(Color.RED);
                    }
                }
            }
        });
    }

    private void handleHelpButtonClick() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        final DocumentReference userDocRef = db.collection("users").document(currentUser.getUid());

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                boolean currentDistressState = documentSnapshot.getBoolean("inDistress") != null && documentSnapshot.getBoolean("inDistress");
                boolean newDistressState = !currentDistressState;
                userDocRef.update("inDistress", newDistressState);

                if (newDistressState) { // If distress signal is activated
                    String parentPhone = documentSnapshot.getString("parentPhone");
                    String childName = documentSnapshot.getString("fullname");
                    List<GeoPoint> locationPath = (List<GeoPoint>) documentSnapshot.get("location_path");

                    if (parentPhone != null && !parentPhone.isEmpty()) {
                        sendSms(parentPhone, childName, locationPath);
                        initiateCall(parentPhone);
                    }
                }
            }
        });
    }

    private void sendSms(String phoneNumber, String childName, List<GeoPoint> locationPath) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                String message = childName + " is in distress! ";
                if(locationPath != null && !locationPath.isEmpty()){
                    GeoPoint lastLocation = locationPath.get(locationPath.size() - 1);
                    message += "Last known location: http://maps.google.com/maps?q=" + lastLocation.getLatitude() + "," + lastLocation.getLongitude();
                }
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(this, "Distress SMS sent.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "SMS failed to send.", Toast.LENGTH_SHORT).show();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQUEST_CODE);
        }
    }

    private void initiateCall(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_PERMISSION_REQUEST_CODE);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        } else {
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                startLocationService();
            }
        } else {
            startLocationService();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allForegroundPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allForegroundPermissionsGranted = false;
                    break;
                }
            }

            if (allForegroundPermissionsGranted) {
                checkBackgroundLocationPermission();
            } else {
                Toast.makeText(this, "Foreground permissions are required for tracking.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService();
            } else {
                Toast.makeText(this, "Background location permission is required for tracking.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We don't recall the method to avoid loops, the user can press again.
            } else {
                Toast.makeText(this, "Call permission is required to contact parent.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SEND_SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We don't recall the method to avoid loops, the user can press again.
            } else {
                Toast.makeText(this, "SMS permission is required to notify parent.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationService() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction(LocationService.ACTION_START_OR_UPDATE);
            serviceIntent.putExtra("userUid", currentUser.getUid());
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_STOP);
        stopService(serviceIntent); // Use stopService for clarity
    }
}
