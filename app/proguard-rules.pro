# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers and source file info for crash reports
-keepattributes SourceFile,LineNumberTable

# Rename source file for obfuscation
-renamesourcefileattribute SourceFile

# Keep Room database entities and DAOs
-keep class com.example.lastdrop.*Entity { *; }
-keep class com.example.lastdrop.LastDropDao { *; }
-keep class com.example.lastdrop.LastDropDatabase { *; }

# Keep GoDice SDK classes (native interface)
-keep class com.techspark.dice.godice.** { *; }
-keep class godice.** { *; }

# Keep BLE service classes
-keep class com.example.lastdrop.ESP32ConnectionManager { *; }
-keep class com.example.lastdrop.MainActivity { *; }

# Keep data classes used for JSON serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp and networking
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep crash reporting attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exception
