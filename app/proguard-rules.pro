# ---------------------------------------------------------------------------
# BeVest R8 / ProGuard rules
# Most libraries (Firebase, Hilt, Compose, Coil, Maps, ML Kit, CameraX) ship
# their own consumer rules, so only app-specific keeps live here.
# ---------------------------------------------------------------------------

# Keep crash stack traces readable in Crashlytics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Domain model ----
# Enum constant names are persisted to Firestore / RTDB as strings and read back
# by name (see Enums.kt `fromRaw`). Renaming the constants would break parsing.
-keepclassmembers enum com.jtexpress.bevest.domain.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Data classes are written to Firestore as maps and read via manual mappers, so
# reflection is not required — but keep their members to be safe against any
# future toObject() use and to keep field names stable in logs.
-keep class com.jtexpress.bevest.domain.model.** { *; }

# ---- Firebase ----
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
# Firestore/RTDB model deserialization via reflection (defensive).
-keepclassmembers class com.jtexpress.bevest.** {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}

# ---- kotlinx.serialization ----
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- Kotlin metadata (for reflection-lite used by Hilt/Compose) ----
-keep class kotlin.Metadata { *; }
