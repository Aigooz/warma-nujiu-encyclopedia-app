# Warma / 怒九 百科 App

这是一个安卓端百科应用，直接内置并打开两个在线可视化百科：

- [Warma 百科](https://aigooz.github.io/warma-encyclopedia/)
- [怒九百科](https://aigooz.github.io/nujiu-encyclopedia/)

## 下载

1. 打开本仓库的 [Releases](https://github.com/Aigooz/warma-nujiu-encyclopedia-app/releases)
2. 下载最新版的 `app-release.apk`
3. 安装到安卓设备

> 首次安装未知来源应用时，系统会提示授权，允许即可。

## 更新

APP 内容会直接加载 GitHub Pages 上的最新网页数据，因此百科内容更新时通常不需要重新安装 APP。

如果 APP 本身升级，只需要：

1. 修改 `app/build.gradle` 中的 `versionCode` 和 `versionName`
2. 提交并打 tag，例如 `v1.0.1`
3. GitHub Actions 会自动构建并发布新的 Release

APP 内也提供“检查更新”入口，会自动跳转到最新 Release 页面。

## 构建

GitHub Actions 会自动构建。需要本地构建时：

```bash
gradle assembleRelease
```
