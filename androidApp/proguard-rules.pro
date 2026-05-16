-keep class com.fortrx.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class io.ktor.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn io.ktor.**
-dontwarn io.ktor.client.**
# FIXED: Enable R8 Minification
