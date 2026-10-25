package com.flashlight.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

public class DeviceInfo {
    public static String getInfo(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ИНФОРМАЦИЯ ОБ УСТРОЙСТВЕ ===\n");
        sb.append("Модель: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Устройство: ").append(Build.DEVICE).append("\n");
        sb.append("Бренд: ").append(Build.BRAND).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Ядро: ").append(System.getProperty("os.version")).append("\n");
        sb.append("Архитектура: ").append(Build.CPU_ABI).append("\n");
        
        // Заряд батареи
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);
            sb.append("Заряд батареи: ").append(batteryPct).append("%\n");
        }
        
        // Уникальный ID устройства
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        sb.append("Android ID: ").append(androidId).append("\n");

        return sb.toString();
    }
}
