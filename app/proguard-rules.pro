# ============================================================================
# Warma / 怒九百科 App — R8 / ProGuard 规则
# ============================================================================
# 本项目数据层完全依赖 Gson 的反射式反序列化（DataRepository.parse 直接把
# JSON 映射到 DataModels.* 的公开字段）。R8 默认会重命名字段，一旦字段名被
# 改写，Gson 就找不到对应 key，结果表现为「APK 变小了但百科数据全空」。
# 因此下面这些 keep 规则是**必需**的，不是可选优化。
# ============================================================================

# --- Gson 反射所需：保持数据模型的字段名与结构 -------------------------------
-keep class com.aigooz.encyclopediaapp.DataModels { *; }
-keep class com.aigooz.encyclopediaapp.DataModels$* { *; }

# 更新任务状态同样经 Gson 读写 SharedPreferences（SettingsFragment.saveDownload）
-keep class com.aigooz.encyclopediaapp.AppRelease { *; }
-keep class com.aigooz.encyclopediaapp.UpdateDownload { *; }

# 答题轮次经 Gson 序列化进 Bundle（QuizFragment.onSaveInstanceState）
-keep class com.aigooz.encyclopediaapp.QuizSession { *; }
-keep class com.aigooz.encyclopediaapp.VideoFilter { *; }

# Gson 需要泛型签名信息才能还原 List<T> 等集合类型
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Gson 内部使用反射访问泛型与注解
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Android 组件：清单中声明的类不能被混淆 ---------------------------------
-keep class com.aigooz.encyclopediaapp.MainActivity { *; }
-keep class com.aigooz.encyclopediaapp.VideoDetailActivity { *; }
-keep class com.aigooz.encyclopediaapp.*Fragment { *; }

# --- MPAndroidChart ---------------------------------------------------------
# 图表库内部使用反射创建 DataSet / Renderer，保留其公开 API
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- Glide ------------------------------------------------------------------
# Glide 4.x 自带 consumer rules，这里只兜底关闭缺失类的告警
-dontwarn com.bumptech.glide.**

# --- 保留行号，便于线上崩溃栈定位 -------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
