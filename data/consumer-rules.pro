# Consumer ProGuard rules for :data
# Persistence models (@Serializable DataStore payloads) are kept by the app
# module's own rules; this file exists so AGP has a real rules file to merge
# instead of warning about a missing one.
-keep class com.actuate.data.storage.** { *; }
