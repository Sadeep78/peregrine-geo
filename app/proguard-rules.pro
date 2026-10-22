# ProGuard / R8 Rules for Peregrine (dev.peregrinegeo.peregrine)

# 1. Preserve kotlinx.serialization serializers and models
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
    kotlinx.serialization.KSerializer serializer();
}
-keep class kotlinx.serialization.** { *; }

# 2. Preserve pure domain & protocol data models
-keep class dev.peregrinegeo.peregrine.core.geo.** { *; }
-keep class dev.peregrinegeo.peregrine.core.kinematics.** { *; }
-keep class dev.peregrinegeo.peregrine.core.location.** { *; }
-keep class dev.peregrinegeo.peregrine.core.network.** { *; }
-keep class dev.peregrinegeo.peregrine.feature.** { *; }

# 3. Preserve Room Database schemas & DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# 4. Official Android Mock Location Permission
# ACCESS_MOCK_LOCATION is intentionally required in the manifest so the app appears
# in Android Developer Options > "Select mock location app" picker.
# Lint suppression: @SuppressLint("MockLocation")
-dontwarn android.location.LocationManager
