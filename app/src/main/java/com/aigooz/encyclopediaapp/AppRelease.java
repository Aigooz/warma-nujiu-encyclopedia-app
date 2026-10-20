package com.aigooz.encyclopediaapp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class AppRelease {
    static final String REPOSITORY = "Aigooz/warma-nujiu-encyclopedia-app";
    static final String PAGE = "https://github.com/" + REPOSITORY + "/releases/latest";
    static final List<String> SOURCE_NAMES = Arrays.asList("GitHub 官方", "备用线路 1 · ghfast.top", "备用线路 2 · gh-proxy.com");
    String tag;
    String url;
    String sha256;
    long size;

    static AppRelease parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("draft") && root.get("draft").getAsBoolean()
                || root.has("prerelease") && root.get("prerelease").getAsBoolean()) {
            throw new IllegalArgumentException("不是正式版本");
        }
        String tag = root.get("tag_name").getAsString();
        if (!tag.matches("v?\\d+\\.\\d+\\.\\d+")) throw new IllegalArgumentException("版本号无效");
        JsonArray assets = root.getAsJsonArray("assets");
        if (assets != null) for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if (!"app-release.apk".equals(asset.get("name").getAsString())) continue;
            AppRelease release = new AppRelease();
            release.tag = tag;
            release.url = asset.get("browser_download_url").getAsString();
            release.size = asset.get("size").getAsLong();
            if (!release.validUrl() || release.size <= 0) throw new IllegalArgumentException("安装包地址或大小无效");
            if (asset.has("digest") && !asset.get("digest").isJsonNull()) {
                String digest = asset.get("digest").getAsString().toLowerCase(Locale.ROOT);
                if (!digest.matches("sha256:[a-f0-9]{64}")) throw new IllegalArgumentException("安装包校验信息无效");
                release.sha256 = digest.substring(7);
            }
            return release;
        }
        throw new IllegalArgumentException("尚未上传安装包");
    }

    boolean validUrl() {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null
                    && ("/" + REPOSITORY + "/releases/download/" + tag + "/app-release.apk").equals(uri.getRawPath());
        } catch (Exception ignored) { return false; }
    }

    String sourceUrl(int source) {
        if (!validUrl()) throw new IllegalArgumentException("安装包地址无效");
        if (source == 0) return url;
        if (source == 1) return "https://ghfast.top/" + url;
        if (source == 2) return "https://gh-proxy.com/" + url;
        throw new IllegalArgumentException("未知下载线路");
    }

    String fileName() { return "encyclopedia-" + tag.replaceFirst("^v", "") + ".apk"; }

    static int compareVersion(String left, String right) {
        String[] a = left.replaceFirst("(?i)^v", "").split("\\.");
        String[] b = right.replaceFirst("(?i)^v", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int result = Integer.compare(i < a.length ? Integer.parseInt(a[i]) : 0,
                    i < b.length ? Integer.parseInt(b[i]) : 0);
            if (result != 0) return result;
        }
        return 0;
    }
}
