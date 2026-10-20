package com.aigooz.encyclopediaapp;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppReleaseTest {
    private String json(String url, String digest) {
        return "{\"tag_name\":\"v1.3.1\",\"assets\":[{\"name\":\"app-release.apk\",\"size\":6000000,"
                + "\"browser_download_url\":\"" + url + "\",\"digest\":" + digest + "}]}";
    }

    private String official() {
        return "https://github.com/" + AppRelease.REPOSITORY + "/releases/download/v1.3.1/app-release.apk";
    }

    @Test public void parsesExactApkAndBuildsHttpsFallbacks() {
        String hash = new String(new char[64]).replace('\0', 'a');
        AppRelease release = AppRelease.parse(json(official(), "\"sha256:" + hash + "\""));
        assertEquals(hash, release.sha256);
        assertEquals(6000000, release.size);
        assertEquals(official(), release.sourceUrl(0));
        assertEquals("https://ghfast.top/" + official(), release.sourceUrl(1));
        assertEquals("https://gh-proxy.com/" + official(), release.sourceUrl(2));
        assertEquals("encyclopedia-1.3.1.apk", release.fileName());
    }

    @Test public void supportsOlderReleasesWithoutDigest() {
        assertNull(AppRelease.parse(json(official(), "null")).sha256);
    }

    @Test public void rejectsUnexpectedSourcesAndMalformedMetadata() {
        String[] invalid = {official().replace("https:", "http:"), official().replace("github.com", "evil.example"),
                official().replace("/Aigooz/", "/other/"), official() + "?file=other", official().replace("v1.3.1", "v1.3.0"),
                official().replace("github.com/", "github.com:443/"), official().replace("github.com", "user@github.com")};
        for (String url : invalid) assertInvalid(json(url, "null"));
        assertInvalid(json(official(), "\"sha256:abc\""));
        assertInvalid(json(official(), "null").replace("6000000", "0"));
        assertInvalid(json(official(), "null").replace("app-release.apk\",\"size", "other.apk\",\"size"));
        assertInvalid(json(official(), "null").replace("{\"tag_name", "{\"draft\":true,\"tag_name"));
        assertInvalid(json(official(), "null").replace("{\"tag_name", "{\"prerelease\":true,\"tag_name"));
    }

    private void assertInvalid(String json) {
        try { AppRelease.parse(json); fail("Expected invalid release"); }
        catch (RuntimeException expected) { /* Invalid input must never produce a download URL. */ }
    }

    @Test public void versionComparisonHandlesPrefixesAndNumericSegments() {
        assertTrue(AppRelease.compareVersion("v1.3.1", "1.3.0") > 0);
        assertTrue(AppRelease.compareVersion("v1.10.0", "1.9.9") > 0);
        assertEquals(0, AppRelease.compareVersion("v1.3.1", "1.3.1"));
        assertTrue(AppRelease.compareVersion("1.2.9", "1.3.0") < 0);
    }

    @Test public void stalledTransfersAdvanceOnlyWithinAvailableSources() {
        UpdateDownload task = new UpdateDownload();
        task.track(0, 1000);
        assertFalse(task.stalled(30_999));
        assertTrue(task.stalled(31_000));
        task.track(100, 31_000);
        assertFalse(task.stalled(31_000));
        task.track(100, 32_000);
        assertEquals(31_000, task.lastProgressAt);
        assertTrue(task.hasNextSource());
        task.source = 2;
        assertFalse(task.hasNextSource());
    }

    @Test public void taskRoundTripRetainsExactReleaseAndDownloadIdentity() {
        UpdateDownload task = new UpdateDownload();
        task.release = AppRelease.parse(json(official(), "null"));
        task.id = 123;
        task.source = 1;
        task.ready = true;
        task.waitingForPermission = true;
        Gson gson = new Gson();
        UpdateDownload restored = gson.fromJson(gson.toJson(task), UpdateDownload.class);
        assertEquals(123, restored.id);
        assertEquals(1, restored.source);
        assertTrue(restored.ready);
        assertTrue(restored.waitingForPermission);
        assertEquals(official(), restored.release.url);
        assertTrue(restored.release.validUrl());
    }
}
