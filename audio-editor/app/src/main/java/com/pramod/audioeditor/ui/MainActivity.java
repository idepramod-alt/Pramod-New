package com.pramod.audioeditor.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pramod.audioeditor.R;

/**
 * Main entry point. File picker → EditorActivity.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_AUDIO = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnOpen = findViewById(R.id.btnOpenFile);
        btnOpen.setOnClickListener(v -> openAudioFile());

        // Handle direct audio intent
        if (getIntent().getData() != null) {
            openEditor(getIntent().getData());
        }
    }

    private void openAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Take persistable permission
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                openEditor(uri);
            }
        }
    }

    private void openEditor(Uri audioUri) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("AUDIO_URI", audioUri.toString());
        startActivity(intent);
    }
}
