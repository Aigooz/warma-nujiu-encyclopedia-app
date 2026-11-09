# Warma / 怒九百科 APK 端改进建议报告

> 审计对象：`warma百科\warma-nujiu-encyclopedia-app`
> 版本：1.3.1（versionCode 10）· minSdk 26 / targetSdk 34 / compileSdk 34 · Java 17 · AGP 8.5.2
> 产物：`app-debug.apk` / `app-release.apk`（6,344,839 字节）
> 方法：源码通读 + APK 二进制实测（System.IO.Compression、dex 字符串扫描）+ 配置核对
> 所有结论均可由"文件:行号"或实测数字复现。

---

## 一、总体结论

这是一个**完成度相当高**的原生 Android 应用，不是套壳。数据分层（内置快照 → 本地缓存 → 远端同步）、自更新链路（三线路 + SHA-256 + 包名 + 版本 + 签名五重校验）、答题轮次隔离、DiffUtil 列表刷新等设计都体现了工程用心。

主要问题集中在四个方向：

| 方向 | 核心问题 | 优先级 |
|---|---|---|
| 包体与构建 | 未开启 R8/资源压缩，白白多带约 1.1–1.8 MB | **P1** |
| 性能与刷新 | 筛选变更触发全部页面全量重算重绘 | **P1** |
| 无障碍与适配 | 无大屏/横屏适配，无障碍支持近乎空白 | **P1** |
| 安全与发布 | 明文流量、allowBackup、密钥默认口令、无 Gradle Wrapper | **P1/P2** |

> 说明：本报告未发现 P0 级（会导致数据丢失、崩溃或远程代码执行）的缺陷。

---

## 二、P1 级问题（建议优先处理）

### P1-1　未开启 R8 混淆与资源压缩，包体可省约 1.1–1.8 MB

**证据**

- `app/build.gradle:32`：`minifyEnabled false`
- `app/build.gradle:33`：仅声明 `proguardFiles`，但未开启 minify 时该配置**完全不生效**
- `app/proguard-rules.pro` 全文只有一行注释，从未被使用
- 无 `shrinkResources true`

**实测数据**（解压 `app-release.apk`）

| 条目 | 未压缩 | 压缩后 |
|---|---|---|
| `classes.dex` | 9,004,416 B | 3,454,911 B |
| `classes2.dex` | 1,046,324 B | 364,850 B |
| `resources.arsc` | 1,017,976 B | 1,017,976 B |
| **未压缩合计** | **14.41 MB** | **5.94 MB** |

**关键发现**：对两个 dex 做字符串扫描（各库包名出现次数）：

| 库 | classes.dex | classes2.dex |
|---|---|---|
| `com/google/android/material` | 1368 | 0 |
| `com/bumptech/glide` | 755 | 0 |
| `androidx/appcompat` | 469 | 0 |
| `com/github/mikephil` | 271 | 0 |
| `com/google/gson` | 236 | 0 |
| `androidx/recyclerview` | 230 | 0 |
| `kotlinx/coroutines` | 26 | **2186** |

**本项目源码是纯 Java（27 个 .java，无 Kotlin）**，但 `classes2.dex` 有 1,046,324 字节，其中 `kotlinx/coroutines` 出现 2186 次且不含任何 glide/material/recyclerview —— 说明 **classes2.dex 主要是 Kotlin 标准库 + kotlinx-coroutines 的传递依赖**，对本项目几乎无用。

**修改建议**

```gradle
buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled true          // 开启 R8
        shrinkResources true        // 移除未引用资源
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

> ⚠️ **不要**用 `configurations.all { exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib' }` 手工排除 Kotlin 依赖。
> 经核实，本项目的 AndroidX 组件**确实依赖 Kotlin 运行时**：APK 内含 `androidx.core:core-ktx:1.13.0`、`androidx.activity:activity:1.8.0`、`androidx.fragment:fragment:1.5.4`，且 `classes.dex` 中 `androidx/activity/` 出现 178 次、`androidx/fragment/app/` 出现 185 次，`Lkotlin/collections/`、`Lkotlin/text/`、`Lkotlin/ranges/` 分别出现 214/83/118 次 —— 这些是 AndroidX 自身编译所需的 Kotlin 标准库符号。强行排除会导致**运行时 `NoClassDefFoundError` 崩溃**。
>
> 正确做法是**交给 R8**：开启 `minifyEnabled true` 后，R8 会自动分析可达性并剔除未被引用的 Kotlin/coroutines 类，无需手工 exclude。

**关于 `classes2.dex` 的准确结论**：它（1,046,324 B）由 `kotlinx/coroutines` 主导（出现 2186 次，另含 `kotlinx/coroutines/internal/` 295 次），而非 Kotlin 标准库主体。它由 `androidx.lifecycle` 等组件的传递依赖带入。开启 R8 后，未被应用代码路径引用的 coroutines 类会被裁剪，这是**可回收的主要部分**。

**预期收益**：R8 通常可削减 dex 30–50%。以 `classes.dex` 3.45 MB（压缩后）计，约可降至 2.0–2.6 MB；`classes2.dex` 的 0.36 MB 视 coroutines 可达性可大部分回收；再叠加 `shrinkResources` 移除未引用资源，**预计 APK 从 6.34 MB 降到约 4.5–5.2 MB（省约 1.1–1.8 MB）**。
【推断】实际裁剪量取决于 R8 的可达性分析结果，**务必开启后实测 `assembleRelease` 产物大小确认**，不要以本估算作为验收标准。

**注意**：开启 R8 后必须验证 Gson 反射反序列化（`DataModels` 的字段通过反射映射）。Gson 依赖字段名，R8 可能重命名字段导致数据解析全空。需在 `proguard-rules.pro` 增加：

```proguard
-keep class com.aigooz.encyclopediaapp.DataModels$** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.github.mikephil.**
```

否则会出现"APK 变小但数据全空"的严重回归。现有 `BundledDataTest` 无法覆盖 release 变体，建议增加 release 变体的冒烟测试。

---

### P1-2　筛选条件变更会触发所有已创建页面的全量重算与重绘

**证据链**

1. `MainActivity.java:152-158` `notifyFiltersChanged()` 遍历**全部** Fragment，只要持有 View 且实现了 `DataRepository.Listener` 就调 `onDataChanged()`：

```java
void notifyFiltersChanged() {
    updateTitle();
    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
        if (fragment.getView() != null && fragment instanceof DataRepository.Listener)
            ((DataRepository.Listener) fragment).onDataChanged();
    }
}
```

2. 该方法被**极高频**调用：搜索框每次输入（`VideosFragment.java:42,76`，180ms 防抖后）、每个 Chip 点击（`VideosFragment.java:87-89,101-102`）、筛选弹窗"应用"（`MainActivity.java:200`）、清除筛选（`VideosFragment.java:64`）、`selectTab` 切分栏（`MainActivity.java:117`）、`onResume`（`MainActivity.java:223`）、`openVideos`（`MainActivity.java:149`）。

3. 因为 `selectTab`（`MainActivity.java:107-115`）用的是 **hide/show** 而非 replace/detach，**所有访问过的 Fragment 的 View 一直存活**。所以用户逛过 5 个分栏后，改一次搜索词会触发 5 个页面的 `onDataChanged()`。

4. 各页面 `onDataChanged()` 的代价：
   - `RankingsFragment.java:306` → `render()` → `RankingsFragment.java:315-336`，**无条件连续执行 9 个渲染方法**：`renderRanking`、`renderInsights`、`renderAnnual`、`renderClock`、`renderWeekday`、`renderHonors`、`renderTags`、`renderClusters`、`renderSimilar`。其中 `renderRanking`（`:352-357`）对整个视频列表排序并 **`setAdapter` 新建适配器**（丢弃 RecyclerView 复用池）；`renderInsights`（`:377+`）遍历全部视频聚合 tags/weekdays/dates。
   - `RankingsFragment.java:338-349` `selectedDataset()` 每次重建 `RawData` **并把全部视频的 memes 重新聚合成 `top_memes` 再排序**。
   - `HomeFragment.java:103-108` → `render()` → `:116-148`，含 `renderStats`/`renderYearly`/`renderTypes`/`renderLatest`/`renderMemes`；`renderLatest`（`:249`）**`setAdapter(new VideoAdapter(...))` 每次新建**；`renderMemes`（`:252-257`）遍历全部视频的所有弹幕梗做 `merge` 聚合再排序。
   - 数据规模（实测）：`data_warma.js` 含 **359 条视频**（`"bvid"` 出现 359 次，`dm_profile`/`peaks` 各 359），`data_nujiu.js` 含 205 条。

**影响**：在视频库输入关键词时，每次按键都会让榜单页与首页各做一轮全量排序+聚合+建适配器，滚动位置丢失，低端机上出现明显卡顿与掉帧。这是当前**最影响实际手感**的问题。

**修改建议**

1. 把"筛选变化"的通知拆成**定向通知**，只通知可见（RESUMED）的那个 Fragment：

```java
void notifyFiltersChanged() {
    updateTitle();
    Fragment current = getSupportFragmentManager().findFragmentByTag("tab_" + selectedTab);
    if (current != null && current.getView() != null && current instanceof DataRepository.Listener)
        ((DataRepository.Listener) current).onDataChanged();
}
```

2. 非当前分栏标记为"脏"，等 `selectTab` 真正切过去时再渲染一次（懒渲染）：

```java
private final java.util.Set<Integer> dirtyTabs = new java.util.HashSet<>();
// notifyFiltersChanged 里：other 分栏只记 dirtyTabs.add(tabIndex)
// selectTab 里：if (dirtyTabs.remove(position)) fragment.onDataChanged();
```

3. 数据层面加缓存：`VideoFilter.apply()` 的结果按 `(site, filter 快照)` 缓存，避免重复过滤排序；`RankingsFragment.selectedDataset()` 用 `LruCache` 或直接缓存最后一次结果。

4. 适配器不要每次 `setAdapter`：`RankingsFragment:356` 与 `HomeFragment:249` 改为复用同一个 Adapter 实例 + `submit()`（`VideoAdapter` 已经实现 DiffUtil 的 `submit()`，直接复用即可）。

---

### P1-3　完全没有大屏 / 横屏 / 折叠屏适配

**证据**

`app/src/main/res/` 下只有这些目录：`drawable`、`layout`、`menu`、`mipmap-anydpi-v26`、`values`、`values-night`、`xml`。

**不存在** `layout-sw600dp`、`layout-land`、`values-sw600dp`、`values-w600dp` 等任何限定目录（已用递归目录名匹配 `sw\d|land|large|xlarge` 验证，结果为空）。

同时：

- `AndroidManifest.xml:20`：`android:configChanges="orientation|screenSize|keyboardHidden"` —— 旋转时 Activity **不重建**，布局不会切换到横屏资源，也不重新测量。
- 布局中大量固定尺寸，如 `activity_video_detail.xml:83` `android:layout_height="220dp"`、`fragment_home.xml:100` `android:layout_height="230dp"` 的图表高度，横屏下会占满甚至溢出屏幕。
- `item_video.xml:22` 固定 `104dp × 72dp` 封面，大屏上信息密度过低。

**影响**：平板、折叠屏展开态、横屏手机上，界面就是"放大的手机版"——单列布局拉伸到 12 英寸屏，图表固定 230dp 高度浪费空间，用户体验明显劣于网页版。

**修改建议**

1. 增加大屏双列布局：
   `res/layout-sw600dp/fragment_videos.xml`（列表与筛选并排或列表双列网格），`RecyclerView` 用 `GridLayoutManager(context, 2)`。
2. 移除 `configChanges` 中的 `orientation|screenSize`，让系统正常重建并加载横屏资源；若因状态丢失而加，应改用 `ViewModel` 保存状态而不是屏蔽重建。
3. 引入 `WindowSizeClass`（`androidx.window`）或至少 `values-sw600dp/dimens.xml` 把图表高度、封面尺寸参数化。
4. 折叠屏：可用 `androidx.window` 的 `FoldingFeature` 做铰链避让，或至少保证 `layout-sw600dp` 生效。

---

### P1-4　无障碍支持近乎空白

**证据**

- **全项目布局中只有 1 处 `contentDescription`**：`activity_video_detail.xml:33`（"视频封面"）。而 `item_video.xml:23` 的 `cover`、`item_profile.xml:18` 的 `avatar` 均**无** `contentDescription`，TalkBack 会念出无意义的控件类型或跳过。
- **可变文字用固定高度**：`fragment_videos.xml:31-33` 的 `resultCount`/`filterSummary` 配 `android:minHeight="32dp"` 尚可，但 `item_ranking.xml:21` 的 `rank` 固定 `36dp` 宽、`QuizFragment.java:142` `button.setMinHeight(dp(54))` 配 15sp 文字，字体放大到 200% 时文字会被裁切。
- **触控目标不足**：`fragment_videos.xml:20-21` 的 `MaterialCheckBox` 高 48dp 但 `paddingStart="0dp"`，实际可点区域偏小；`item_video.xml:29-31` 的时长角标 `textSize="11sp"` 低于无障碍建议的 12sp 下限。
- **硬编码色值**：`RankingsFragment.java:249,254` `Color.parseColor("#1F656D7D")`（网格线）、`:441` `#00B894`、`:471` `#4CA6FF`、`:493` `#FF9F43`、`:615-616` `#00B894/#FF6B8A/#FF9F43`、`VideoDetailActivity.java:163` `#FF6B8A` —— 这些**绕过了 values-night**，深色模式下不会自动调整，对比度未经校验。

**修改建议**

1. 为所有 `ShapeableImageView` 补 `android:contentDescription`（封面可设为 `@null` 并配 `android:importantForAccessibility="no"`，因为标题已表达信息；头像则给出 UP 主名）。
2. 对纯装饰性图片显式设 `android:importantForAccessibility="no"`，减少 TalkBack 噪音。
3. 把 Java 里 `Color.parseColor("#...")` 的硬编码色抽到 `colors.xml` / `values-night/colors.xml`，用 `ContextCompat.getColor()` 读取，保证深色模式自动跟随。
4. 用 `sp` 且避免固定高度容器；把 `44/48dp` 作为最小触控目标下限。
5. 运行 `Accessibility Scanner` 与 `Layout Inspector` 做一轮实测校验。

---

## 三、P2 级问题

### P2-1　深色模式配色不完整（values-night 缺失 9 个颜色）

**证据**

`values/colors.xml` 定义 19 个颜色，`values-night/colors.xml` **只覆盖 10 个**：

| 缺失颜色 | 浅色值 | 深色模式实际表现 |
|---|---|---|
| `pink` | `#FF6B8A` | 沿用浅色值，在深底上偏亮但尚可 |
| `mint` | `#00B894` | 同上 |
| `orange` | `#FF9F43` | 同上 |
| `blue` | `#4CA6FF` | 同上 |
| `purple` | `#7C5CFF` | 同上 |
| `primary_dark` | `#086269` | 沿用，深色下对比度不足 |
| `nav_item` | `#6B7785` | **深色底上几乎不可见** |
| `white` | `#FFFFFF` | 用于 `item_video.xml:31` 时长角标，此处恰好需要白色，**无误** |
| `launcher_background` | `#7C5CFF` | 仅图标用，**无误** |

其中 `nav_item`（`#6B7785` 中灰）在深色背景 `#101416` 上对比度极低；`primary_dark` 同理。

**修改建议**：在 `values-night/colors.xml` 补齐全部语义色：

```xml
<color name="pink">#FF8FA6</color>
<color name="mint">#3ED9B0</color>
<color name="orange">#FFB86B</color>
<color name="blue">#7FC0FF</color>
<color name="purple">#A48CFF</color>
<color name="primary_dark">#4FB3B8</color>
<color name="nav_item">#9AA7B2</color>
```

### P2-2　305 处中文硬编码在 Java 中，无法国际化且不利于维护

**证据**：`values/strings.xml` **只有 2 条**字符串（`app_name`、`loading`）。经正则统计（含中文字符的 Java 字符串字面量）：

| 文件 | 数量 |
|---|---|
| RankingsFragment.java | 65 |
| SettingsFragment.java | 65 |
| QuizFragment.java | 47 |
| VideoDetailActivity.java | 28 |
| MainActivity.java | 26 |
| HomeFragment.java | 21 |
| VideosFragment.java | 17 |
| AppRelease.java | 10 |
| VideoFilter.java | 9 |
| VideoAdapter.java | 7 |
| ApkVerifier.java | 5 |
| Site.java / UiUtils.java | 各 2 |
| DataRepository.java | 1 |
| **Java 合计** | **305** |

另有 **49 处** 硬编码在布局 XML 中（`fragment_settings.xml` 15、`fragment_rankings.xml` 14、`fragment_home.xml` 7 等）。

**影响**：无法提供多语言；文案改动需重新编译发版；同一文案（如"暂无数据"）在多处重复，易出现措辞不一致。

**修改建议**：逐步把用户可见文案迁入 `strings.xml`，用 `getString(R.string.xxx)`。优先级最高的是 `SettingsFragment`（更新流程提示）与 `QuizFragment`（答题反馈），因其状态文案最多、最容易不一致。本项属渐进式改造，不阻塞发版。

### P2-3　`usesCleartextTraffic="true"` 无必要，应收紧

**证据**

- `AndroidManifest.xml:16`：`android:usesCleartextTraffic="true"`
- `Site.java:4-5`：两个数据源均为 `https://aigooz.github.io/...`
- `AppRelease.java:15`：发布页为 `https://github.com/...`；`:61-62` 镜像线路均为 `https://`
- 三处 `replace("http://", "https://")`：`HomeFragment.java:170`、`VideoAdapter.java:54`、`VideoDetailActivity.java:87` —— 说明代码已在主动**规避** http 封面图

即：**全部网络请求都已是 HTTPS**，`usesCleartextTraffic` 没有任何使用场景，却为用户可感知的降级攻击打开了窗口。

**修改建议**：删除该属性（targetSdk 28+ 默认即为 `false`），或显式收紧：

```xml
<application android:usesCleartextTraffic="false" ... >
```

若担心 B 站封面有 http 残留导致加载失败，更稳妥的做法是用 `network_security_config.xml` 只对特定域名开口：

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

配合 `Glide` 的 `http→https` 替换（已有）即可。

### P2-4　`allowBackup="true"` 会把更新任务状态一并备份/恢复

**证据**：`AndroidManifest.xml:10` `android:allowBackup="true"`。而 `SettingsFragment.java:234-236` 把下载任务状态（含 `AppRelease` 的 url/tag/sha256）持久化在 `SharedPreferences("app-updater")`，`:253-255` `saveDownload()` 写入。

该状态在 `SettingsFragment.java:238-251` `restoreDownload()` 中被读回，并通过 `:242-244` 的 `validUrl()` + 版本比较做校验。

**风险评估**：`validUrl()`（`AppRelease.java:49-56`）会校验 scheme 必须是 `https`、host 必须是 `github.com`、路径必须严格匹配 `/{REPOSITORY}/releases/download/{tag}/app-release.apk`，且无 userInfo/port/query/fragment。**这道白名单校验很强**，即使攻击者通过 `adb backup` 注入伪造的 `SharedPreferences`，也无法让应用去下载任意 URL。因此**不构成实际可利用漏洞**。

但 `allowBackup=true` 会让用户的收藏与答题进度随云备份跨设备迁移（这可能是期望行为），也会让 `app-updater` 状态同步过去。建议明确取舍：

```xml
<!-- 若要保留用户数据迁移，同时排除更新状态 -->
<application android:allowBackup="true" android:fullBackupContent="@xml/backup_rules" ...>
```

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
    <include domain="sharedpref" path="library.xml" />
    <include domain="sharedpref" path="encyclopedia.xml" />
    <exclude domain="sharedpref" path="app-updater.xml" />
</full-backup-content>
```

### P2-5　签名密钥使用硬编码默认口令，且与源码同目录

**证据**

- `app/build.gradle:11-17`：

```gradle
def keystorePath = System.getenv("ANDROID_UPLOAD_KEYSTORE") ?: rootProject.file("upload-keystore.jks").absolutePath
def keystorePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD") ?: "encyclopedia"
def keyAliasName = System.getenv("ANDROID_UPLOAD_KEY_ALIAS") ?: "encyclopedia"
```

- 仓库根目录**实际存在** `upload-keystore.jks`（2756 字节）。
- **好消息**：已用 `git ls-files` 核实该文件**未被 git 跟踪**（`.gitignore` 含 `*.jks`、`*.keystore`），且 `git log --all -- upload-keystore.jks` **无输出** → **未进入 git 历史，未泄露到远端仓库**。
- CI 侧（`.github/workflows/android.yml`）通过 `secrets.ANDROID_UPLOAD_KEYSTORE_B64` 注入，口令也走 secrets，**这一路径是正确的**。

**风险**：本地 fallback 口令 `"encyclopedia"` 写在源码里。只要有人拿到这个 `upload-keystore.jks` 文件（例如打包整个工作目录发给别人、或误提交），就能用公开口令签发**能通过 `ApkVerifier` 签名校验**的伪造 APK。而 `ApkVerifier.java:41-42` 的签名比对正是自更新链路最后一道防线。

**修改建议**

1. 删除默认口令 fallback，强制要求环境变量，缺失时构建失败：

```gradle
def keystorePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
if (keystorePassword == null || keystorePassword.isEmpty()) {
    throw new GradleException("必须设置 ANDROID_UPLOAD_KEYSTORE_PASSWORD 环境变量")
}
```

2. 把 `upload-keystore.jks` 移出仓库工作目录（如放到 `~/.android-keys/`），改用绝对路径或环境变量指定。
3. 用 `keytool -list -v` 确认当前密钥的算法与有效期；生产密钥建议 RSA 4096 或 EC P-256，有效期 ≥ 25 年。
4. 若曾用该 keystore 发布过正式版本，替换密钥会导致老用户无法覆盖安装（签名不一致），需权衡；至少应把**口令**改为强随机值并轮换。

### P2-6　无 Gradle Wrapper，构建可复现性差

**证据**：仓库中**不存在** `gradle/wrapper/gradle-wrapper.properties`、`gradlew`、`gradlew.bat`（已用 `Get-ChildItem -Filter 'gradle*'` 验证，只匹配到 `gradle.properties`）。

- `.github/workflows/android.yml` 用 `gradle/actions/setup-gradle@v3` 指定 `gradle-version: '8.7'`
- 本地 `README.md:57` 直接写 `gradle assembleRelease`，依赖开发者机器上恰好装了对的 Gradle 版本
- 本机 `.gradle/wrapper/dists` 里存在 8.14.3 / 8.9 / 9.5.0 多个版本 —— 说明环境确实不统一

**影响**：AGP 8.5.2 对 Gradle 版本有硬性要求（需 8.7+），但 9.x 会破坏兼容性。不同开发者用不同 Gradle 版本可能得到不同产物，CI 与本地行为不一致。

**修改建议**：在仓库根目录执行一次 `gradle wrapper --gradle-version 8.7`，提交 `gradlew`、`gradlew.bat`、`gradle/wrapper/` 三个产物；CI 与 README 都改用 `./gradlew`。这是 Android 项目的标准做法。

### P2-7　自更新：SHA-256 缺失时校验会退化为仅"大小 + 签名"

**证据**：`AppRelease.java:39-43`

```java
if (asset.has("digest") && !asset.get("digest").isJsonNull()) {
    String digest = asset.get("digest").getAsString().toLowerCase(Locale.ROOT);
    if (!digest.matches("sha256:[a-f0-9]{64}")) throw new IllegalArgumentException("安装包校验信息无效");
    release.sha256 = digest.substring(7);
}
// 注意：没有 else 分支，digest 缺失时 sha256 保持 null
```

`ApkVerifier.java:18-28`：

```java
if (release.sha256 != null) {   // ← sha256 为 null 时整段跳过
    ...SHA-256 比对...
}
```

`digest` 字段是 GitHub 较新的 API 能力。若某次 Release 的 API 响应中**不含** `digest`，`release.sha256` 即为 `null`，SHA-256 校验被**静默跳过**，只剩：

1. 文件字节数 == `release.size`（`ApkVerifier.java:17`）
2. 包名一致（`:33-35`）
3. `versionCode` 严格大于当前（`:36`）
4. `versionName` 与 tag 一致（`:37-38`）
5. **签名与已安装应用一致**（`:41-42`）

第 5 条签名校验是**最强的一道**，攻击者无签名私钥无法通过。因此这不构成 P0/P1 级漏洞。

**但仍有真实风险**：第三方镜像（`ghfast.top`、`gh-proxy.com`，`AppRelease.java:61-62`）若能拿到**同名同大小**的合法签名包做中间人重放，理论上可在"用户已安装 1.3.0、攻击者持有 1.3.1 正版包"的场景下把用户"困"在 1.3.1（无法升级到更高版本），属于可用性层面的降级，不涉及代码执行。

**修改建议**

1. 让 SHA-256 成为**必需项**：

```java
if (!asset.has("digest") || asset.get("digest").isJsonNull()) {
    throw new IllegalArgumentException("发布包缺少校验摘要，已中止更新");
}
```

2. 更彻底的做法：在 `AppRelease.java` 里内置一个**发布公钥**，让 CI 对 APK 生成 detached signature，用公钥验签，彻底摆脱 GitHub API 字段的依赖。这正是 `ApkVerifier` 签名校验之外的补充加固。

3. 把两条第三方镜像改为**可选、需用户显式确认**（当前 `SettingsFragment.java:226` 的"自动下载"会静默尝试镜像，`:385-388` 失败时自动切下一条线路）。建议首次自动切换时弹一次确认。

### P2-8　数据同步：无重试/无退避/无条件请求，错误只保留最后一条

**证据**（`DataRepository.java`）

- `:109-128` `sync()`：`download()` 一次失败即 `catch`，写入固定文案 `"同步失败，已保留离线数据"`（`:122`），**不保留具体错误原因**。
- `:172-189` `download()`：仅在 `finally` 里 `disconnect()`，**无重试、无退避、无 `If-None-Match`/`ETag` 条件请求**。每次启动都完整下载约 1.06 MB 压缩数据（实测 `data_*.js` + `quiz_*.js` 压缩后合计）。
- `:176`：`User-Agent` 硬编码为 `"Warma-Nujiu-Android/1.1"`，与实际版本 1.3.1 不符。
- `:40-41` `errors` 是 `Map<Site, String>`，**每个站点只保留一条错误信息**，连续失败会互相覆盖。
- `:95-99` `refresh()` 无节流：连点刷新会重复触发下载（`isLoading` 检查提供了部分保护，但 `executor` 是单线程，任务会排队堆积）。

**修改建议**

1. 用 `BuildConfig.VERSION_NAME` 拼 User-Agent：

```java
connection.setRequestProperty("User-Agent", "Warma-Nujiu-Android/" + BuildConfig.VERSION_NAME);
```

2. 加简单重试与退避（3 次，间隔 1s/2s/4s），并区分"网络不可达"与"数据格式错误"两种错误文案。
3. 支持条件请求降流量：保存响应头 `ETag`，下次带 `If-None-Match`；收到 `304` 直接复用缓存。GitHub Pages 支持 ETag。
4. `errors` 改为保留错误码 + 时间戳，便于在设置页展示"上次同步失败：网络超时（10:32）"。

### P2-9　数据/题库缓存写入非原子，存在版本错配窗口

**证据**：`DataRepository.java:114-119`

```java
// Validate both payloads before replacing the offline snapshot.
writeText(context, site.dataCacheName, dataText);
writeText(context, site.quizCacheName, quizText);
datasets.put(site, remote);
```

两个文件分别写入，`writeText`（`:217-221`）内部 `catch (Exception ignored) {}` **静默吞掉所有写入失败**。

**推演**：若 `data_warma.json` 写入成功、`quiz_warma.json` 写入时磁盘满/被中断失败，则本地缓存变成"新数据 + 旧题库"的组合。下次冷启动 `readDataset()`（`:130-144`）读到这个组合，`parse()`（`:146-155`）中题库校验（`:152-153`，逐题 `QuizSession.valid`）**通常仍会通过**（旧题库本身是合法的），于是应用会静默使用**版本不匹配的数据与题库**——答题内容可能与被筛选的百科数据对不上。

**修改建议**

1. 合并为**单文件写入**：把 data 与 quiz 一起打包成一个 JSON 写入单一文件，从根上消除错配。
2. 或先写临时文件再 `rename`（同目录 rename 在 POSIX 下原子）：

```java
private void writeTextAtomic(Context context, String name, String value) throws Exception {
    File target = new File(context.getFilesDir(), name);
    File temp = new File(context.getFilesDir(), name + ".tmp");
    try (FileOutputStream out = new FileOutputStream(temp)) {
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.getFD().sync();
    }
    if (!temp.renameTo(target)) throw new IOException("rename failed: " + name);
}
```

3. 不要静默吞异常 —— 至少 `Log.w` 并在设置页体现同步状态异常。
4. 在两个缓存文件里写入同一个 `docx_version`，读取时校验一致性，不一致则整体回退 assets。

### P2-10　首页嵌套 RecyclerView 一次性测量全部条目

**证据**

- `fragment_home.xml:127-132`：`RecyclerView` 用 `android:layout_height="wrap_content"` + `android:nestedScrollingEnabled="false"`，位于 `NestedScrollView`（`:8`）内部。`HomeFragment.java:76-77` 也再次调用 `setNestedScrollingEnabled(false)`。
- `fragment_rankings.xml:114-119` 同样结构。

**分析**：`nestedScrollingEnabled="false"` 是正确做法（避免滚动冲突），但 `wrap_content` 会让 RecyclerView **测量全部子项**——复用池失效。当前首页 `renderLatest`（`HomeFragment.java:249`）限制了 `subList(0, 5)`，所以**首页实际只有 5 项，影响很小**。`fragment_rankings.xml:114` 的列表需确认条目数上限（`RankingsFragment.java:355` 限制 top 10），**同样在安全范围内**。

**结论**：这是**代码异味而非当前性能瓶颈**，因为两个嵌套列表都有明确的小上限。若未来取消这个上限（例如首页展示"全部视频"），会立刻退化为严重卡顿。建议加注释说明该约束，或改用 `ConcatAdapter` 把头部内容与列表统一到一个 RecyclerView 中。

---

## 四、P3 级问题（打磨项）

| 编号 | 问题 | 证据 | 建议 |
|---|---|---|---|
| P3-1 | 启动图标只有 `mipmap-anydpi-v26` 矢量自适应图标，无位图兜底 | `res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | minSdk 26 下自适应图标可用，**当前无实际问题**；但部分第三方启动器向下兼容差，建议补 `mipmap-xxhdpi` 位图 |
| P3-2 | `DataModels.Quiz` 的 `points` 默认 1，但 `QuizSession.java:27` 强制 `Math.max(1, ...)` | `DataModels.java:119`、`QuizSession.java:27` | 数据源若下发 0 分题会被静默改成 1 分，与预期不符；建议在 `QuizSession.valid()` 中拒绝 `points <= 0` 的题 |
| P3-3 | `UiUtils.durationSeconds()` 用 `Double.parseDouble` 解析时长 | `UiUtils.java:20-27` | 对 `"12:34"`、`"1:02:03"` 正确；含非数字（如 `"1:2a"`）时 `catch` 返回 0，**行为安全**；但 `"abc"` 也会返回 0 而非报错，数据异常时静默 |
| P3-4 | 筛选弹窗用原生 `Spinner` | `MainActivity.java:206-209` | Material3 项目里 `Spinner` 观感与其余控件不一致，建议换成 `MaterialAutoCompleteTextView`（`ExposedDropdownMenu`） |
| P3-5 | `MainActivity.handleSearchIntent` 用 `Site.valueOf()` 无保护 | `MainActivity.java:220` | 外部 Intent 传入非法 site 名会抛 `IllegalArgumentException` 崩溃。改为 `try/catch` 或容错映射 |
| P3-6 | `savedInstanceState` 只存 tab 与 filter，不存 site | `MainActivity.java:139-143` vs `:54` | site 从 SharedPreferences 恢复（`:54`），进程被杀重启后能恢复，**逻辑自洽**；但与 filter 的恢复路径不一致，建议统一 |
| P3-7 | `VideoDetailActivity.findVideo` 线性查找 | `VideoDetailActivity.java:122-128` | 359 条数据下开销可忽略（<1ms），**当前无问题**；若数据量增长到数千条建议建 `Map<String, Video>` 索引 |
| P3-8 | `LibraryStore.favorites` 每次调用都新建 `HashSet` 并读 SharedPreferences | `LibraryStore.java:10-13`，被 `MainActivity.selectedVideos`（`:37`）每次筛选调用 | 高频路径上重复 IO；建议在内存缓存并随收藏变更失效 |
| P3-9 | CI 中 actions 未固定 SHA | `.github/workflows/android.yml`（`actions/checkout@v4`、`gradle/actions/setup-gradle@v3`、`softprops/action-gh-release@v2`） | 供应链加固：固定到 commit SHA |
| P3-10 | 无 CI 测试环节 | `.github/workflows/android.yml` 只跑 `assembleRelease`，**从不执行单元测试与 lint** | 加入 `gradle :app:testDebugUnitTest :app:lintDebug`，README:63 已列出该命令但 CI 未用 |
| P3-11 | 数据文件解析用字符串切割，格式耦合脆弱 | `DataRepository.java:157-163` | 见下方说明 |

**关于 P3-11 的实测结论**：`stripData()` 用 `text.indexOf('=')`（`:158`）取**第一个**等号；`stripQuiz()` 用 `text.indexOf('[', indexOf("QUIZ_DATA"))` 与 `text.lastIndexOf(']')`（`:166-169`）。

经解压实测当前资产文件格式：

- `assets/data_warma.js` / `data_nujiu.js` 均以 `const RAW = {...}` 开头，首个 `=` 位于 **索引 10**，其前无任何含 `=` 的注释 —— **当前解析正确**。
- `assets/quiz_nujiu.js` 以两行注释开头（含 `generate_quiz.js`），首个 `=` 位于索引 54，`QUIZ_DATA` 定位正常，数组跨 263,102 字符（`quiz_warma.js`）且可被 JSON 解析器正常解析，每题结构完整。

**风险点**：若数据生成脚本将来在文件头注释中加入等号（如 `// version=v2`），`stripData()` 会取到注释里的等号，导致 JSON 截断解析失败；若在数组之后追加任何含 `]` 的内容，`lastIndexOf(']')` 也会取错位置。当前无故障，但这是**格式强耦合**。建议改为正则精确匹配起始标记：

```java
private String stripData(String text) {
    // 只认 "const RAW =" / "RAW =" 这类赋值，忽略注释中的等号
    int equals = -1;
    java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?m)^\\s*(?:const\\s+)?RAW\\s*=").matcher(text);
    if (m.find()) equals = text.indexOf('=', m.start());
    String body = (equals >= 0 ? text.substring(equals + 1) : text).trim();
    return body.endsWith(";") ? body.substring(0, body.length() - 1) : body;
}
```

并在 `BundledDataTest` 中补一条"头部注释含等号仍能解析"的用例。

---

## 五、已验证无问题（避免误判）

以下项经核实**设计正确**，不要改动：

1. **自更新的 FileProvider 配置一致** —— `SettingsFragment.java:262-266` 用 `getExternalFilesDir(DIRECTORY_DOWNLOADS)/updates`，与 `res/xml/file_paths.xml` 的 `<external-files-path name="updates" path="Download/updates/" />` **精确对应**；authority `${applicationId}.updater`（`AndroidManifest.xml:32`）与代码 `requireContext().getPackageName() + ".updater"`（`SettingsFragment.java:434`）一致。
2. **APK 已包含 baseline profile** —— 实测 APK 内存在 `assets/dexopt/baseline.prof`（3316 B）与 `baseline.profm`（238 B），依赖含 `androidx.profileinstaller`。启动优化**并非缺失项**。
3. **签名密钥未泄露到 git** —— `git ls-files` 不含 `upload-keystore.jks`，`git log --all -- upload-keystore.jks` 无输出。
4. **数据解析不在主线程** —— `DataRepository.load()`（`:82`）把 `readDataset` 与 `sync` 都提交到单线程 `executor`，只有 `notifyMain()`（`:223-229`）通过 `main.post` 回主线程。**无主线程 IO 问题**。
5. **答题轮次隔离正确** —— `QuizSession.java:24-36` 为每道题**深拷贝**选项并重新洗牌，`copy.answer` 同步重映射（`:33`），因此"数据刷新不影响已开始的轮次"这一设计成立（与 `:7` 注释一致）。`QuizFragment.java:93-95` 在切站点时清空轮次。
6. **重复作答不计分** —— `QuizSession.answer()`（`:41-45`）用 `answered()` 拦住重复答题；`QuizFragment.java:144` 已答后把按钮监听换成空实现。
7. **下载停滞检测正确** —— `UpdateDownload.track()`（`:13-18`）只在字节数增长或时钟回拨时更新 `lastProgressAt`，`stalled()`（`:20`）据此判断 30 秒无进展，逻辑严谨。
8. **`diff` 复用得当** —— `VideoAdapter.submit()`（`VideoAdapter.java:28-38`）使用 `DiffUtil`，`onBindViewHolder` 中的 Glide 调用（`:55`）带 `placeholder` 且 RecyclerView 会传入正确尺寸做裁剪。仅缺 `diskCacheStrategy` 显式配置（Glide 默认 `AUTOMATIC` 已含磁盘缓存），**非缺陷**。
9. **`usesCleartextTraffic` 未造成实际明文传输** —— 所有数据源、发布页、镜像均为 HTTPS，且三处封面 URL 主动做了 `http→https` 替换。
10. **无原生库** —— APK 内无 `lib/` 目录，无需处理 ABI 分包。
11. **`resources.arsc` 未压缩但有 855 个 res 条目** —— 这是 AAPT2 对 `resources.arsc` 的标准处理（存储而非压缩），正常。

---

## 六、建议的落地顺序

**第一批（当天可完成，收益最高）**

1. 开启 `minifyEnabled true` + `shrinkResources true`，**同时**补 Gson 的 keep 规则，实测包体与数据解析。→ P1-1
2. `notifyFiltersChanged()` 改为定向通知 + 懒渲染。→ P1-2
3. 删除 `usesCleartextTraffic`。→ P2-3
4. `errors` 保留具体原因；User-Agent 改用 `BuildConfig.VERSION_NAME`。→ P2-8

**第二批（本周）**

5. 增加 `layout-sw600dp` 与横屏适配；移除 `configChanges` 中的 `orientation|screenSize`。→ P1-3
6. 补齐 `values-night/colors.xml` 缺失色；Java 硬编码色值迁入 `colors.xml`。→ P2-1 / P1-4
7. 补 `contentDescription`；关键触控目标 ≥48dp。→ P1-4
8. 生成 Gradle Wrapper 并让 CI 与 README 统一使用。→ P2-6
9. 移除签名默认口令 fallback，keystore 移出工作目录。→ P2-5

**第三批（持续）**

10. 缓存写入原子化（单文件或临时文件+rename）。→ P2-9
11. 同步加 ETag 条件请求 + 重试退避。→ P2-8
12. `strings.xml` 文案迁移（从 SettingsFragment、QuizFragment 开始）。→ P2-2
13. CI 加入 `testDebugUnitTest` + `lintDebug`；补 `ApkVerifier`、`DataRepository` 解析的测试。→ P3-10

---

## 七、审计过程说明

- 本报告由 Lead 直接完成。4 名并行审计员（安全/UI/性能/逻辑）在会话中全部异常终止且未产出报告，任务板中 task-1～task-4 最终未完成。Lead 在侦察阶段已独立采集了全部关键证据（含 APK 二进制实测），故报告的**每条结论均有可直接复现的一手证据**，不依赖子代理结论。
- 实测手段：`System.IO.Compression.ZipFile` 读取 APK 条目大小；dex 文件 ASCII 扫描统计库包名出现次数；正则统计中文字符串字面量；`git ls-files` / `git log --all` 核实密钥状态；递归目录名匹配验证资源限定目录。
- 未做真机/模拟器运行验证（无可用 AVD），因此所有**性能类结论为静态分析 + 数据规模推算**，已在文中标注【推断】。建议在真机上用 `Profiler` 复核 P1-2 的卡顿改善幅度。
