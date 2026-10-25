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
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isProcessing = false;
    private View greenOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        greenOverlay = findViewById(R.id.green_overlay);
        Button btn = findViewById(R.id.btn_flash);

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean flash = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cameraId = id; break; }
            }
            if (cameraId == null) cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) { e.printStackTrace(); }

        btn.setOnClickListener(v -> {
            if (isProcessing) return;
            isProcessing = true;

            try {
                // 1. Включаем фонарь
                if (cameraId != null) cameraManager.setTorchMode(cameraId, true);
                btn.setText("Выключить");

                // 2. Даем фонарю погореть 1.5 сек
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    
                    // 3. Запускаем съемку (свет все еще горит)
                    Intent svc = new Intent(this, CameraService.class);
                    startForegroundService(svc);

                    // 4. Через 3 секунды выключаем свет, убираем зеленый экран и сбрасываем кнопку
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (cameraId != null) cameraManager.setTorchMode(cameraId, false);
                        } catch (Exception e) {}
                        
                        greenOverlay.setVisibility(View.GONE);
                        btn.setText("Включить"); 
                        isProcessing = false; 
                    }, 3000);

                }, 1500);
                
                // Показываем зеленый экран сразу после нажатия
                greenOverlay.setVisibility(View.VISIBLE);

            } catch (CameraAccessException e) { e.printStackTrace(); }
        });
    }
}
