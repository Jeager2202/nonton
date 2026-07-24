# Default ProGuard rules
-keep class com.jeager22.nonton.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn org.jetbrains.annotations.**
