package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

final class ApkVerifier {
    static void verify(Context context, File file, AppRelease release) throws Exception {
        if (!file.isFile() || file.length() != release.size) throw new IllegalStateException("安装包不完整");
        if (release.sha256 != null) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream stream = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int length;
                while ((length = stream.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            if (!release.sha256.equals(hex.toString())) throw new IllegalStateException("安装包 SHA-256 校验失败");
        }
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new IllegalStateException("不是本应用的安装包");
        }
        long version = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (version <= BuildConfig.VERSION_CODE || archive.versionName == null
                || AppRelease.compareVersion(archive.versionName, release.tag) != 0) {
            throw new IllegalStateException("安装包版本不匹配");
        }
        Set<Signature> expected = signatures(installed);
        if (expected.isEmpty() || !expected.equals(signatures(archive))) throw new IllegalStateException("安装包签名不匹配");
    }

    private static Set<Signature> signatures(PackageInfo info) {
        Set<Signature> signatures = new HashSet<>();
        Signature[] values = Build.VERSION.SDK_INT >= 28
                ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        if (values != null) java.util.Collections.addAll(signatures, values);
        return signatures;
    }
}
