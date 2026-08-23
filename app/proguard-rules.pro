# SHOSHI + LUNA v1.7 project-specific R8 rules.
# Keep Room database metadata and model fields referenced by generated adapters.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Preserve generic/signature metadata used by Retrofit/Gson.
-keepattributes Signature
-keepattributes *Annotation*

# Do not suppress warnings globally. Any release shrinker error must be fixed explicitly.
