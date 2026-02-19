package com.example.safestep1;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

// A helper class to provide singleton instances of Firebase services.
// This relies on the automatic initialization from the google-services.json file.
public class FirebaseHelper {

    private static FirebaseFirestore firestoreInstance;
    private static FirebaseAuth authInstance;

    /**
     * Returns the singleton instance of FirebaseFirestore.
     * This is automatically configured by the google-services.json file.
     */
    public static FirebaseFirestore getFirestore() {
        if (firestoreInstance == null) {
            firestoreInstance = FirebaseFirestore.getInstance();
        }
        return firestoreInstance;
    }

    /**
     * Returns the singleton instance of FirebaseAuth.
     * This is automatically configured by the google-services.json file.
     */
    public static FirebaseAuth getFirebaseAuthentication() {
        if (authInstance == null) {
            authInstance = FirebaseAuth.getInstance();
        }
        return authInstance;
    }
}
