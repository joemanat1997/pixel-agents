# Keep the encrypted-preferences stack (Tink uses reflection internally).
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**

# Custom views inflated from XML are kept by the default Android rules via their
# (Context, AttributeSet) constructor, so no extra rules are needed here.
