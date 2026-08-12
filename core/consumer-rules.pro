# Keep public API / models used via reflection or Parcelable bridges
-keep class com.xmoney.payments.model.** { *; }
-keep class com.xmoney.payments.config.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
