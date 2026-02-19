package com.example.safestep1;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class User {

    private static final String TAG = "User";

    // Fields
    private String uid;
    private String name;
    private String email;
    private String password;
    private String phone;
    private String parentPhone; // Null if user is a parent
    private String imageURL;
    private UserType userType;

    // Firebase services
    private FirebaseAuth firebaseAuthentication;
    private FirebaseFirestore database;

    private Context context;

    public static final String COLLECTION_NAME = "users";
    public static final String CURRENT_USER_FILE = "currentUserFile";

    public enum UserType {
        PARENT, CHILD
    }

    // Callbacks
    public interface UserCallback {
        void onFinished(boolean success);
    }

    public interface UsernameCallback {
        void onFound(String fullname);
    }

    // Empty constructor for Firestore
    public User() {}

    // Main constructor for registration
    public User(Context context, String name, String email, String password, String phone, String parentPhone, String imageURL, UserType userType) {
        this.context = context;
        this.firebaseAuthentication = FirebaseHelper.getFirebaseAuthentication();
        this.database = FirebaseHelper.getFirestore();

        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.parentPhone = parentPhone;
        this.imageURL = imageURL;
        this.userType = userType;
    }

    // Login constructor
    public User(Context context, String email, String password) {
        this.context = context;
        this.firebaseAuthentication = FirebaseHelper.getFirebaseAuthentication();
        this.database = FirebaseHelper.getFirestore();
        this.email = email;
        this.password = password;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public String getImageURL() {
        return imageURL;
    }

    public UserType getUserType() {
        return userType;
    }

    public void register(UserCallback callback) {
        firebaseAuthentication.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()) {
                        Log.d(TAG, "Firebase Auth user created successfully.");
                        this.uid = authTask.getResult().getUser().getUid();
                        saveUserToFirestore(callback);
                    } else {
                        Log.e(TAG, "Failed to create Firebase Auth user", authTask.getException());
                        callback.onFinished(false);
                    }
                });
    }

    public void login(UserCallback callback) {
        firebaseAuthentication.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // The UI will be updated in MainActivity, so we just need to report success.
                        callback.onFinished(true);
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        callback.onFinished(false);
                    }
                });
    }

    private void saveUserToFirestore(UserCallback callback) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", this.uid);
        userMap.put("email", this.email);
        userMap.put("fullname", this.name);
        userMap.put("imageURL", this.imageURL);
        userMap.put("phone", this.phone);
        userMap.put("userType", this.userType.name()); // Store enum as a string

        // Only add parentPhone if it exists (for child accounts)
        if (this.parentPhone != null && !this.parentPhone.isEmpty()) {
            userMap.put("parentPhone", this.parentPhone);
        }

        database.collection(COLLECTION_NAME).document(this.uid)
                .set(userMap)
                .addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful()) {
                        Log.d(TAG, "User data saved to Firestore successfully.");
                        callback.onFinished(true);
                    } else {
                        Log.e(TAG, "Failed to save user data to Firestore", dbTask.getException());
                        callback.onFinished(false);
                    }
                });
    }
}
