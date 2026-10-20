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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<Site, DataModels.Dataset> datasets = new ConcurrentHashMap<>();
    private volatile Site selectedSite = Site.WARMA;
    private final Map<Site, Boolean> loading = new ConcurrentHashMap<>();
    private final Map<Site, String> errors = new ConcurrentHashMap<>();

    public boolean isLoading(Site site) { return loading.getOrDefault(site, false); }
    public String error(Site site) { return errors.getOrDefault(site, ""); }

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
        return datasets.get(selectedSite);
    }

    public DataModels.Dataset current(Site site) {
        return datasets.get(site);
    }

    public void load(Context context, Site site) {
        selectedSite = site;
        if (isLoading(site)) { notifyMain(); return; }
        loading.put(site, true);
        errors.remove(site);
        DataModels.Dataset cached = datasets.get(site);
        if (cached != null) {
            notifyMain();
        }
        executor.execute(() -> {
            DataModels.Dataset local = readDataset(context, site);
            if (local != null && datasets.get(site) == null) {
                datasets.put(site, local);
                if (selectedSite == site) {
                    notifyMain();
                }
            }
            preload(context);
            sync(context, site, datasets.get(site));
        });
    }

    public void refresh(Context context, Site site) {
        if (isLoading(site)) return;
        loading.put(site, true); errors.remove(site); notifyMain();
        executor.execute(() -> sync(context, site, readDataset(context, site)));
    }

    private void preload(Context context) {
        for (Site site : Site.values()) {
            if (datasets.get(site) != null) continue;
            DataModels.Dataset local = readDataset(context, site);
            if (local != null) datasets.put(site, local);
        }
    }

    private void sync(Context context, Site site, DataModels.Dataset fallback) {
        try {
            String dataText = download(site.dataUrl);
            String quizText = download(site.quizUrl);
            DataModels.Dataset remote = parse(site, dataText, quizText, false);
            if (remote != null && remote.data != null && remote.data.videos != null && !remote.data.videos.isEmpty()) {
                // Validate both payloads before replacing the offline snapshot.
                writeText(context, site.dataCacheName, dataText);
                writeText(context, site.quizCacheName, quizText);
                datasets.put(site, remote);
            } else throw new IllegalStateException("Empty remote dataset");
        } catch (Exception e) {
            Log.w(TAG, "Sync failed", e);
            errors.put(site, "同步失败，已保留离线数据");
            if (fallback != null) datasets.putIfAbsent(site, fallback);
        } finally {
            loading.put(site, false);
            notifyMain();
        }
    }

    private DataModels.Dataset readDataset(Context context, Site site) {
        try {
            String dataText = readCache(context, site.dataCacheName);
            String quizText = readCache(context, site.quizCacheName);
            if (dataText == null) dataText = readAsset(context, site.dataAssetName);
            if (quizText == null) quizText = readAsset(context, site.quizAssetName);
            try { return parse(site, dataText, quizText, true); }
            catch (Exception invalidCache) {
                return parse(site, readAsset(context, site.dataAssetName), readAsset(context, site.quizAssetName), true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Read local failed", e);
            return null;
        }
    }

    private DataModels.Dataset parse(Site site, String dataText, String quizText, boolean fromCache) {
        DataModels.RawData data = gson.fromJson(stripData(dataText), DataModels.RawData.class);
        if (data == null || data.videos == null || data.videos.isEmpty()) throw new IllegalStateException("Missing video data");
        for (DataModels.Video video : data.videos) video.duration_seconds = UiUtils.durationSeconds(video);
        Type quizType = new TypeToken<List<DataModels.Quiz>>() {}.getType();
        List<DataModels.Quiz> quiz = gson.fromJson(stripQuiz(quizText), quizType);
        if (quiz == null || quiz.isEmpty() || quiz.stream().anyMatch(q -> !QuizSession.valid(q)))
            throw new IllegalStateException("Invalid quiz data");
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
        try {
        int code = connection.getResponseCode();
        if (code != 200) throw new IllegalStateException("HTTP " + code);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        } finally { connection.disconnect(); }
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
