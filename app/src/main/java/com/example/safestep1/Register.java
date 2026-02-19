package com.example.safestep1;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Register extends AppCompatActivity {

    // UI Components
    private EditText edtName, edtEmail, edtPassword, edtConfirm, edtPhone, edtParentPhone;
    private SwitchCompat switchUserType;
    private Button btnSubmit;
    private ImageView addPhoto;
    private ProgressDialog progressDialog;

    // Member variables
    private boolean profilePhotoExists = false;
    private byte[] fileBytes;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PHOTO_REQUEST = 2;
    private String verificationId;

    private String[] permissionGroup = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize UI components
        edtName = findViewById(R.id.edtfullname);
        edtEmail = findViewById(R.id.edtemail);
        edtPassword = findViewById(R.id.edtpassword);
        edtConfirm = findViewById(R.id.edtconfirmpassword);
        edtPhone = findViewById(R.id.edtphone);
        edtParentPhone = findViewById(R.id.edtparentphone);
        switchUserType = findViewById(R.id.switchUserType);
        btnSubmit = findViewById(R.id.btnsubmit);
        addPhoto = findViewById(R.id.addPhoto);

        // Request permissions if not granted
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissionGroup, 100);
        }

        // Set listeners
        addPhoto.setOnClickListener(v -> showPhotoOptions());
        btnSubmit.setOnClickListener(v -> handleSubmit());

        // Handle user type switch logic
        switchUserType.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtParentPhone.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void handleSubmit() {
        String name = edtName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();
        String confirm = edtConfirm.getText().toString();
        String phone = edtPhone.getText().toString().trim();
        String parentPhone = edtParentPhone.getText().toString().trim();
        boolean isChild = switchUserType.isChecked();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty() || !profilePhotoExists) {
            Toast.makeText(this, "Please fill all fields and add a photo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isChild && parentPhone.isEmpty()) {
            Toast.makeText(this, "Please enter your parent's phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog(isChild ? "Sending verification code..." : "Registering...");

        if (isChild) {
            startPhoneNumberVerification(parentPhone);
        } else {
            uploadProfileImage(name, email, password, phone, null, false);
        }
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        String formattedPhoneNumber = phoneNumber;
        if (formattedPhoneNumber.startsWith("0")) {
            formattedPhoneNumber = formattedPhoneNumber.substring(1);
        }
        formattedPhoneNumber = "+972" + formattedPhoneNumber;

        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                formattedPhoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mCallbacks);        // OnVerificationStateChangedCallbacks
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            progressDialog.dismiss();
            signInWithPhoneAuthCredential(credential);
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            progressDialog.dismiss();
            Toast.makeText(Register.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
            progressDialog.dismiss();
            Register.this.verificationId = verificationId;
            promptForVerificationCode();
        }
    };

    private void promptForVerificationCode() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String code = input.getText().toString();
            verifyPhoneNumberWithCode(verificationId, code);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Verification successful, proceed with registration
                        String name = edtName.getText().toString().trim();
                        String email = edtEmail.getText().toString().trim();
                        String password = edtPassword.getText().toString();
                        String phone = edtPhone.getText().toString().trim();
                        String parentPhone = edtParentPhone.getText().toString().trim();
                        uploadProfileImage(name, email, password, phone, parentPhone, true);
                    } else {
                        // Verification failed
                        Toast.makeText(Register.this, "Verification failed. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadProfileImage(String name, String email, String password, String phone, String parentPhone, boolean isChild) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference("ProFileImages/" + email);

        storageRef.putBytes(fileBytes).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageUrl = uri.toString();
                registerUserInDatabase(name, email, password, phone, parentPhone, imageUrl, isChild);
            });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void registerUserInDatabase(String name, String email, String password, String phone, String parentPhone, String imageUrl, boolean isChild) {
        User.UserType userType = isChild ? User.UserType.CHILD : User.UserType.PARENT;
        String finalParentPhone = isChild ? parentPhone : null;

        User user = new User(this, name, email, password, phone, finalParentPhone, imageUrl, userType);

        user.register(success -> {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(Register.this, "Registration successful!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Register.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(Register.this, "Registration failed. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // --- Utility and other methods ---
    private boolean hasPermissions() {
        for (String permission : permissionGroup) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void showPhotoOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Add your profile image");
        builder.setNegativeButton("צילום תמונה", (dialog, which) -> takePhoto());
        builder.setPositiveButton("בחירת תמונה", (dialog, which) -> selectPhoto());
        builder.show();
    }

    private void selectPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PHOTO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Bitmap bitmap = null;
            if (requestCode == PICK_IMAGE_REQUEST) {
                Uri imageUri = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == TAKE_PHOTO_REQUEST) {
                bitmap = (Bitmap) data.getExtras().get("data");
            }

            if (bitmap != null) {
                addPhoto.setImageBitmap(bitmap);
                prepareFileBytes(bitmap);
                profilePhotoExists = true;
            }
        }
    }

    private void prepareFileBytes(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        fileBytes = baos.toByteArray();
    }

    private void showProgressDialog(String status) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setMessage(status);
        progressDialog.show();
    }
}
