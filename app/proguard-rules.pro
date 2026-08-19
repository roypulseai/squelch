# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Google API
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**

# Keep data classes for JSON serialization
-keep class com.squelch.app.crypto.VaultPayload { *; }
-keep class com.squelch.app.crypto.VaultPayload$ContactEntry { *; }
-keep class com.squelch.app.crypto.VaultPayload$Settings { *; }
