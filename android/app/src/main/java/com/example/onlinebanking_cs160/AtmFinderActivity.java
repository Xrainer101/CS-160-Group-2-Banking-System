package com.example.onlinebanking_cs160;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Finds Chase ATMs near an address the user types. Uses Google's Maps URLs, which open the Google
 * Maps app (or maps.google.com in a browser) already searching "Chase ATM near <address>" — no API
 * key, billing or location permission needed.
 *
 * To show a map INSIDE the app instead, you'd add the Maps SDK + Places API with a Google Cloud
 * API key (billing must be turned on for that key).
 */
public class AtmFinderActivity extends AppCompatActivity {
    private static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps";
    static final String BANK = "Chase";

    private EditText editAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_atm_finder);
        Ui.setUpScreen(this, "Find ATM");

        editAddress = findViewById(R.id.editAddress);
        findViewById(R.id.buttonFindAtms).setOnClickListener(v -> search());

        // The keyboard's search key does the same as the button
        editAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
    }

    private void search() {
        String address = editAddress.getText().toString().trim();
        if (address.isEmpty()) {
            editAddress.setError("Enter your address, city or ZIP code");
            editAddress.requestFocus();
            return;
        }
        openMaps(buildQuery(address));
    }

    /** "Chase ATM near 1 Washington Sq, San Jose, CA 95192" */
    static String buildQuery(String address) {
        return BANK + " ATM near " + address.trim();
    }

    /** https://www.google.com/maps/search/?api=1&query=Chase%20ATM%20near%20... */
    static String mapsUrl(String query) {
        return "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query);
    }

    private void openMaps(String query) {
        Uri uri = Uri.parse(mapsUrl(query));
        Intent maps = new Intent(Intent.ACTION_VIEW, uri);
        maps.setPackage(GOOGLE_MAPS_PACKAGE);
        try {
            startActivity(maps); // Google Maps app
        } catch (ActivityNotFoundException noMapsApp) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri)); // browser
            } catch (ActivityNotFoundException nothing) {
                Ui.toast(this, "Install Google Maps or a web browser to find ATMs.");
            }
        }
    }
}