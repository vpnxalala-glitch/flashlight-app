package com.flashlight.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private Button flashButton;
    private boolean isOn = false;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    findFlashCamera();
                } else {
                    Toast.makeText(this, "Для работы фонарика требуется разрешение камеры",
                            Toast.LENGTH_LONG).show();
                    updateButton();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flashButton = findViewById(R.id.btn_flash);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        flashButton.setOnClickListener(v -> toggleFlash());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            findFlashCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
        updateButton();
    }

    private void findFlashCamera() {
        if (cameraManager == null) {
            showUnavailable();
            return;
        }

        try {
            cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                showUnavailable();
            } else {
                updateButton();
            }
        } catch (CameraAccessException | SecurityException e) {
            showUnavailable();
        }
    }

    private void toggleFlash() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA);
            return;
        }

        if (cameraManager == null || cameraId == null) {
            findFlashCamera();
            if (cameraId == null) return;
        }

        try {
            boolean nextState = !isOn;
            cameraManager.setTorchMode(cameraId, nextState);
            isOn = nextState;
            updateButton();
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            isOn = false;
            updateButton();
            Toast.makeText(this, "Не удалось переключить вспышку. Попробуйте ещё раз.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateButton() {
        if (flashButton != null) {
            flashButton.setText(isOn ? "Выключить" : "Включить");
            flashButton.setEnabled(cameraId != null
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void showUnavailable() {
        cameraId = null;
        isOn = false;
        updateButton();
        Toast.makeText(this, "На устройстве не найдена доступная вспышка",
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (isOn && cameraManager != null && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException | IllegalArgumentException | SecurityException ignored) {
                // The camera may already be unavailable while the activity is closing.
            }
        }
        super.onDestroy();
    }
}
