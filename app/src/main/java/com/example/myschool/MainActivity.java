package com.example.myschool;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.UnstableApi;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.common.InputImage;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.OnClickListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private DatabaseReference databaseReference;
    private static final int CAMERA_REQUEST_CODE = 101;

    TextView firebaseTextView, sname, sroll, sclass, smobile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        firebaseTextView = findViewById(R.id.firebaseTextView);
        sname = findViewById(R.id.sname);
        sroll = findViewById(R.id.sroll);
        sclass = findViewById(R.id.sclass);
        smobile = findViewById(R.id.smobile);
        // Initialize Firebase Realtime Database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("students");

        previewView = findViewById(R.id.previewView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))  // High resolution for better scanning
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            @androidx.camera.core.ExperimentalGetImage
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                scanQRCode(image, imageProxy);
            }
        });

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void scanQRCode(InputImage image, ImageProxy imageProxy) {
        BarcodeScanner scanner = BarcodeScanning.getClient();
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String qrCodeValue = barcode.getRawValue();  // Capture the QR code value here

                        if (qrCodeValue != null) {
                            // Mark attendance using the scanned QR code value
                            firebaseTextView.setText(qrCodeValue);
                            fetchStudentData();



                        Toast.makeText(this, "QR Code Scanned: " + qrCodeValue, Toast.LENGTH_SHORT).show();
                            imageProxy.close();  // Close after processing
                            return;  // Stop scanning after the first code
                        }
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to scan QR code", Toast.LENGTH_SHORT).show();
                    imageProxy.close();
                });
    }

    private void markAttendance(String qrCodeValue) {
        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        String currentTime = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date());
        String status = "Present";
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference().child("Attendance").child(currentDate).child(qrCodeValue);
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("A_Date", currentDate);
                map.put("B_Time", currentTime);
                map.put("C_Class", sclass.getText().toString());
                map.put("D_Roll", sroll.getText().toString());
                map.put("E_Name", sname.getText().toString());
                map.put("F_Status",status );
                attendanceRef.setValue(map) // Directly overwrite the data
                        .addOnSuccessListener(unused -> {
                            showCustomDialog();
                        //    Toast.makeText(getApplicationContext(), "Attendance marked", Toast.LENGTH_SHORT).show();

                            /////////////////////////////

                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getApplicationContext(), "Failed to mark attendance", Toast.LENGTH_SHORT).show();

                        });
            }


            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchStudentData() {
        // Ensure QR value is not null or empty
        String qrValue = firebaseTextView.getText().toString().trim();
        if (qrValue.isEmpty()) {
            Toast.makeText(MainActivity.this, "QR code value is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reference to the Firebase Database
        DatabaseReference databaseReference = FirebaseDatabase.getInstance()
                .getReference("18hq9xwC4BuK2mzgPPMoj2aikGj7b5Wcfw1EfzcBnj9Q")
                .child("MAIN")
                .child(qrValue);

        // Attach a ValueEventListener to the database reference
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Get the student data from the snapshot
                ModelClass student = dataSnapshot.getValue(ModelClass.class);

                // Check if student data is null
                if (student != null) {
                    sname.setText(student.getNAME());
                    sclass.setText(student.getS_CLASS());
                    sroll.setText(student.getROLL());
                    smobile.setText(student.getMOBILE());
                    markAttendance(qrValue);
                } else {
                    // If the student is null, notify the user
                    Toast.makeText(MainActivity.this, "Student data not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Log the error and show a Toast to notify the user
                Log.e("MainActivity", "Failed to load data: " + databaseError.getMessage());
                Toast.makeText(MainActivity.this, "Failed to load data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void showCustomDialog() {
        fetchStudentData();

        String qr_CodeValue = firebaseTextView.getText().toString().trim();
        String qr_sname = sname.getText().toString().trim();
        String qr_sclass = sclass.getText().toString().trim();
        String qr_sroll = sroll.getText().toString().trim();

        DialogPlus dialog = DialogPlus.newDialog(this)
                .setGravity(Gravity.CENTER)
                .setContentHolder(new com.orhanobut.dialogplus.ViewHolder(R.layout.popup_attendance)) // Use the custom layout
                .setCancelable(true)  // Allow dismissing by touching outside
                .setExpanded(true)    // Expand the dialog height to full screen
                .setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(DialogPlus dialog, View view) {
                        // Handle button clicks inside the dialog
                        if (view.getId() == R.id.dialog_button) {

                            dialog.dismiss(); // Close dialog when OK button is clicked
                        }
                    }
                })
                .create();

        // Set the dialog title and button behavior
        View contentView = dialog.getHolderView();
        TextView titleTextView = contentView.findViewById(R.id.sname);
        TextView ssroll = contentView.findViewById(R.id.sroll);
        TextView ssclass = contentView.findViewById(R.id.sclass);
        Button okButton = contentView.findViewById(R.id.dialog_button);

        ssroll.setText(qr_sroll);
        ssclass.setText(qr_sclass);
        titleTextView.setText(qr_sname);
        okButton.setText("Ok"); // Set dynamic button text if needed

        dialog.show(); // Show the dialog
    }

}

