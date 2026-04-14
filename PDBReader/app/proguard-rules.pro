# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
