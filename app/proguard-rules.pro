-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Added: the app's JSON models carry no @SerializedName, so the rule above keeps nothing for them.
# Without this, R8 renames their fields and every server reply would parse as empty.
-keep class com.makaohub.android.data.** { *; }
