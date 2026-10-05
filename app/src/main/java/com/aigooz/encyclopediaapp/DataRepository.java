package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataRepository {
    public interface Listener {
        void onDataChanged();
    }

    private static final String TAG = "DataRepository";
    private static volatile DataRepository instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final List<Listener> listeners = new ArrayList<>();
    private volatile DataModels.Dataset dataset;

    private DataRepository() {}

    public static DataRepository get() {
        if (instance == null) {
            synchronized (DataRepository.class) {
                if (instance == null) instance = new DataRepository();
            }
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public DataModels.Dataset current() {
        return dataset;
    }

    public void load(Context context, Site site) {
        executor.execute(() -> {
            DataModels.Dataset local = readDataset(context, site, false);
            if (local != null) {
                dataset = local;
                notifyMain();
                sync(context, site, local);
            } else {
                sync(context, site, null);
            }
        });
    }

    public void refresh(Context context, Site site) {
        executor.execute(() -> sync(context, site, null));
    }

    private void sync(Context context, Site site, DataModels.Dataset fallback) {
        try {
            String dataText = download(site.dataUrl);
            String quizText = download(site.quizUrl);
            writeText(context, site.dataCacheName, dataText);
            writeText(context, site.quizCacheName, quizText);
            DataModels.Dataset remote = parse(site, dataText, quizText, false);
            if (remote != null && remote.data != null && remote.data.videos != null && !remote.data.videos.isEmpty()) {
                dataset = remote;
                notifyMain();
            } else if (fallback != null) {
                dataset = fallback;
                notifyMain();
            }
        } catch (Exception e) {
            Log.w(TAG, "Sync failed", e);
            if (fallback != null && dataset == null) {
                dataset = fallback;
                notifyMain();
            }
        }
    }

    private DataModels.Dataset readDataset(Context context, Site site, boolean ignoreCache) {
        try {
            String dataText = readCache(context, site.dataCacheName);
            String quizText = readCache(context, site.quizCacheName);
            boolean cache = dataText != null && quizText != null;
            if (dataText == null) dataText = readAsset(context, site.dataAssetName);
            if (quizText == null) quizText = readAsset(context, site.quizAssetName);
            return parse(site, dataText, quizText, cache);
        } catch (Exception e) {
            Log.w(TAG, "Read local failed", e);
            return null;
        }
    }

    private DataModels.Dataset parse(Site site, String dataText, String quizText, boolean fromCache) {
        DataModels.RawData data = gson.fromJson(stripData(dataText), DataModels.RawData.class);
        Type quizType = new TypeToken<List<DataModels.Quiz>>() {}.getType();
        List<DataModels.Quiz> quiz = gson.fromJson(stripQuiz(quizText), quizType);
        if (quiz == null) quiz = Collections.emptyList();
        return new DataModels.Dataset(site, data, quiz, fromCache);
    }

    private String stripData(String text) {
        int equals = text.indexOf('=');
        int result = equals >= 0 ? equals + 1 : 0;
        String body = text.substring(result).trim();
        if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
        return body;
    }

    private String stripQuiz(String text) {
        int key = text.indexOf("QUIZ_DATA");
        int start = text.indexOf('[', key < 0 ? 0 : key);
        int end = text.lastIndexOf(']');
        return text.substring(start, end + 1);
    }

    private String download(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "Warma-Nujiu-Android/1.1");
        connection.setRequestProperty("Accept", "*/*");
        int code = connection.getResponseCode();
        if (code != 200) throw new IllegalStateException("HTTP " + code);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String readAsset(Context context, String name) throws Exception {
        try (InputStream input = context.getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
            return builder.toString();
        }
    }

    private String readCache(Context context, String name) {
        try {
            File file = new File(context.getFilesDir(), name);
            if (!file.exists()) return null;
            try (InputStream input = context.openFileInput(name);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) builder.append(line).append('\n');
                return builder.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void writeText(Context context, String name, String value) {
        try (java.io.FileOutputStream output = context.openFileOutput(name, Context.MODE_PRIVATE)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void notifyMain() {
        main.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onDataChanged();
            }
        });
    }
}
