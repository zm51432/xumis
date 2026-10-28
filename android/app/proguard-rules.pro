# 保留 Retrofit / Gson 模型，避免混淆后 JSON 解析失败
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.shine.xumis.app.model.** { *; }
