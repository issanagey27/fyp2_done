package com.example.mytravelapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mytravelapp.R;
import com.example.mytravelapp.adapters.UserSectionsPagerAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class UserPlansActivity extends AppCompatActivity {

    private static final String TAG = "UserPlansActivity";

    private String destinationId;
    private String userEmail;
    private String planName;

    private ImageView planImage;
    private TextView textViewPlanName;
    private EditText editTextPlanName;
    private TextView textViewDestination;
    private FirebaseFirestore db;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private boolean isEditMode = false;
    private final List<String> tabTitles = Arrays.asList("Accommodation", "Restaurants", "Things To Do");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_plans);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        planImage = findViewById(R.id.imageViewPlan);
        textViewPlanName = findViewById(R.id.textViewPlanName);
        editTextPlanName = findViewById(R.id.editTextPlanName);
        textViewDestination = findViewById(R.id.textViewDestination);
        ImageButton goToSettingsButton = findViewById(R.id.settingsButton);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        destinationId = getIntent().getStringExtra("destinationId");
        planName = getIntent().getStringExtra("planName");
        userEmail = getIntent().getStringExtra("userEmail");

        Log.d(TAG, "destinationId: " + destinationId);
        Log.d(TAG, "userEmail: " + userEmail);
        Log.d(TAG, "planName: " + planName);

        if (destinationId == null || userEmail == null || planName == null) {
            Log.e(TAG, "One or more required intent extras are null");
            Toast.makeText(this, "Error: Missing plan details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up ViewPager2 and adapter
        viewPager.setAdapter(new UserSectionsPagerAdapter(this, tabTitles, db, destinationId, userEmail, planName));
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles.get(position))
        ).attach();

        // Fetch details from Firestore
        fetchPlanDetails();

        goToSettingsButton.setOnClickListener(v -> toggleEditMode());

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(UserPlansActivity.this, Recommendation.class);
            intent.putExtra("destinationId", destinationId);
            intent.putExtra("planName", planName);
            intent.putExtra("userEmail", userEmail);
            startActivity(intent);
            finish();
        });
    }

    private void fetchPlanDetails() {
        if (userEmail == null || planName == null) {
            Log.e(TAG, "userEmail or planName is null");
            return;
        }

        DocumentReference planRef = db.collection("user_plans")
                .document(userEmail)
                .collection("plans")
                .document(planName);

        planRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String imageUrl = documentSnapshot.getString("imageUrl");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Log.d(TAG, "Image URL: " + imageUrl);
                    Glide.with(getApplicationContext())
                            .load(imageUrl)
                            .centerCrop()
                            .into(planImage);
                } else {
                    Log.e(TAG, "Image URL is null or empty");
                }

                // Populate views with plan details
                String name = documentSnapshot.getString("name");
                textViewPlanName.setText(name);
                textViewDestination.setText(destinationId);
            } else {
                Toast.makeText(this, "Plan not found", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Plan not found in Firestore");
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error fetching plan details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching plan details", e);
        });
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            textViewPlanName.setVisibility(View.GONE);
            editTextPlanName.setVisibility(View.VISIBLE);
        } else {
            textViewPlanName.setVisibility(View.VISIBLE);
            editTextPlanName.setVisibility(View.GONE);

            // Update the plan name in Firestore if changed
            String newPlanName = editTextPlanName.getText().toString().trim();
            if (!newPlanName.isEmpty() && !newPlanName.equals(planName)) {
                updatePlanName(newPlanName);
            } else {
                // No changes made, just reset the view
                editTextPlanName.setText(planName);
            }
        }
    }

    private void updatePlanName(String newPlanName) {
        DocumentReference oldPlanRef = db.collection("user_plans")
                .document(userEmail)
                .collection("plans")
                .document(planName);

        DocumentReference newPlanRef = db.collection("user_plans")
                .document(userEmail)
                .collection("plans")
                .document(newPlanName);

        oldPlanRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> data = new HashMap<>(documentSnapshot.getData());
                data.put("name", newPlanName);

                newPlanRef.set(data)
                        .addOnSuccessListener(aVoid -> {
                            copySubcollections(oldPlanRef, newPlanRef, () -> {
                                deleteDocumentWithSubcollections(oldPlanRef, () -> {
                                    Toast.makeText(this, "Plan name updated successfully", Toast.LENGTH_SHORT).show();
                                    planName = newPlanName;

                                    // Reload the activity with the new plan name
                                    Intent intent = new Intent(UserPlansActivity.this, UserPlansActivity.class);
                                    intent.putExtra("destinationId", destinationId);
                                    intent.putExtra("planName", newPlanName);
                                    intent.putExtra("userEmail", userEmail);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                            });
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error creating new plan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Error creating new plan", e);
                        });
            } else {
                Toast.makeText(this, "Plan not found", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Plan not found in Firestore");
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error fetching plan details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching plan details", e);
        });
    }

    private void copySubcollections(DocumentReference fromDoc, DocumentReference toDoc, Runnable onComplete) {
        String[] subcollectionNames = {"accommodations", "restaurants", "thingstodo"};
        AtomicInteger pendingCopies = new AtomicInteger(subcollectionNames.length);

        for (String subcollectionName : subcollectionNames) {
            copySubcollection(fromDoc.collection(subcollectionName), toDoc.collection(subcollectionName), () -> {
                if (pendingCopies.decrementAndGet() == 0) {
                    onComplete.run();
                }
            });
        }
    }

    private void copySubcollection(CollectionReference fromCollection, CollectionReference toCollection, Runnable onComplete) {
        fromCollection.get().addOnSuccessListener(queryDocumentSnapshots -> {
            AtomicInteger pendingCopies = new AtomicInteger(queryDocumentSnapshots.size());

            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                toCollection.document(document.getId()).set(document.getData())
                        .addOnSuccessListener(aVoid -> {
                            if (pendingCopies.decrementAndGet() == 0) {
                                onComplete.run();
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error copying document: " + document.getId(), e));
            }

            if (queryDocumentSnapshots.isEmpty()) {
                onComplete.run();
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error getting subcollection", e));
    }

    private void deleteDocumentWithSubcollections(DocumentReference docRef, Runnable onComplete) {
        String[] subcollectionNames = {"accommodations", "restaurants", "thingstodo"};
        AtomicInteger pendingDeletes = new AtomicInteger(subcollectionNames.length);

        for (String subcollectionName : subcollectionNames) {
            deleteSubcollection(docRef.collection(subcollectionName), () -> {
                if (pendingDeletes.decrementAndGet() == 0) {
                    docRef.delete().addOnSuccessListener(aVoid -> {
                        onComplete.run();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Error deleting old plan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error deleting old plan", e);
                    });
                }
            });
        }
    }

    private void deleteSubcollection(CollectionReference collectionRef, Runnable onComplete) {
        collectionRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            AtomicInteger pendingDeletes = new AtomicInteger(queryDocumentSnapshots.size());

            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                document.getReference().delete()
                        .addOnSuccessListener(aVoid -> {
                            if (pendingDeletes.decrementAndGet() == 0) {
                                onComplete.run();
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error deleting document: " + document.getId(), e));
            }

            if (queryDocumentSnapshots.isEmpty()) {
                onComplete.run();
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error deleting subcollection", e));
    }
}
