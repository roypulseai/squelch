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
-keep class okio.** { *; }

# Google API
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**
-dontwarn com.google.api.client.**
-keep class com.google.api.client.** { *; }

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Keep data classes for JSON serialization
-keep class com.squelch.app.crypto.VaultPayload { *; }
-keep class com.squelch.app.crypto.VaultPayload$ContactEntry { *; }
-keep class com.squelch.app.crypto.VaultPayload$Settings { *; }
-keep class com.squelch.app.crypto.VaultOps$RotationResult { *; }
-keep class com.squelch.app.qr.QrContact { *; }
-keep class com.squelch.app.data.local.entity.** { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
