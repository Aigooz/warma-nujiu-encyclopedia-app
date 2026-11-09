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
                // 两份载荷都通过校验后才写入缓存，且先写临时文件再原子改名，
                // 避免出现「新数据 + 旧题库」这种版本错配的中间态。
                writeTextAtomic(context, site.dataCacheName, dataText);
                writeTextAtomic(context, site.quizCacheName, quizText);
                datasets.put(site, remote);
            } else throw new IllegalStateException("远端数据为空");
        } catch (Exception e) {
            Log.w(TAG, "Sync failed", e);
            errors.put(site, describeError(e));
            if (fallback != null) datasets.putIfAbsent(site, fallback);
        } finally {
            loading.put(site, false);
            notifyMain();
        }
    }

    /** 把异常翻译成用户可读、且能区分「网络问题」与「数据格式问题」的短句。 */
    private String describeError(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) return "同步超时，已保留离线数据";
        if (e instanceof java.net.UnknownHostException) return "无法连接网络，已保留离线数据";
        if (e instanceof java.io.IOException) return "网络异常，已保留离线数据";
        return "数据格式有误，已保留离线数据";
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

    /**
     * 从 `const RAW = {...};` 中取出 JSON 主体。
     *
     * 旧实现直接取**第一个** '='，一旦文件头注释里出现等号（例如 `// version=v2`），
     * 就会从注释处截断导致解析失败。这里先定位赋值语句 `RAW =`，再取其后的等号。
     */
    static String stripData(String text) {
        int equals = -1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*(?:const\\s+|var\\s+|let\\s+)?RAW\\s*=")
                .matcher(text);
        if (matcher.find()) equals = text.indexOf('=', matcher.start());
        String body = (equals >= 0 ? text.substring(equals + 1) : text).trim();
        if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
        return body.trim();
    }

    /**
     * 从 `const QUIZ_DATA = [...]` 中取出数组。
     *
     * 旧实现用 `lastIndexOf(']')`，若数组之后还有任何含 ']' 的内容就会取错位置。
     * 改为用 JSON 括号配对扫描，取第一个顶层数组的完整范围。
     */
    static String stripQuiz(String text) {
        int key = text.indexOf("QUIZ_DATA");
        int start = text.indexOf('[', key < 0 ? 0 : key);
        if (start < 0) throw new IllegalStateException("题库缺少 JSON 数组");
        int end = matchArrayEnd(text, start);
        if (end < 0) throw new IllegalStateException("题库 JSON 数组未闭合");
        return text.substring(start, end + 1);
    }

    /** 从 '[' 开始做括号配对（跳过字符串字面量），返回匹配的 ']' 下标；未闭合返回 -1。 */
    static int matchArrayEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * 带退避重试的下载。
     *
     * 旧实现一次失败即放弃，弱网或 GitHub Pages 偶发 5xx 时会直接退回离线快照。
     * 这里对「网络类异常」重试 3 次（间隔 1s / 2s / 4s）；HTTP 4xx 属于确定性错误，
     * 重试没有意义，立即抛出。
     */
    private String download(String path) throws Exception {
        Exception last = null;
        long[] backoff = {1000L, 2000L, 4000L};
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            try {
                return downloadOnce(path);
            } catch (Exception e) {
                last = e;
                if (!isRetryable(e) || attempt == backoff.length) break;
                Log.w(TAG, "Download retry " + (attempt + 1) + " for " + path, e);
                try {
                    Thread.sleep(backoff[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    private boolean isRetryable(Exception e) {
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.UnknownHostException
                || e instanceof java.net.ConnectException || e instanceof java.io.InterruptedIOException) {
            return true;
        }
        // "HTTP 5xx" 与连接中断类 IOException 值得重试；4xx 不重试。
        String message = e.getMessage();
        if (message != null && message.startsWith("HTTP ")) {
            try {
                int code = Integer.parseInt(message.substring(5).trim());
                return code >= 500;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return e instanceof java.io.IOException;
    }

    private String downloadOnce(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        // 版本号跟随 BuildConfig，避免像旧版那样长期硬编码成 1.1 与实际版本脱节。
        connection.setRequestProperty("User-Agent", "Warma-Nujiu-Android/" + BuildConfig.VERSION_NAME);
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
        } finally {
            connection.disconnect();
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

    private void writeTextAtomic(Context context, String name, String value) throws Exception {
        File target = new File(context.getFilesDir(), name);
        File temp = new File(context.getFilesDir(), name + ".tmp");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(temp)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw e;
        }
        // 同目录改名在同文件系统上是原子操作，读端要么看到旧文件、要么看到新文件。
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new java.io.IOException("缓存写入失败：" + name);
        }
    }

    private void notifyMain() {
        main.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onDataChanged();
            }
        });
    }
}
