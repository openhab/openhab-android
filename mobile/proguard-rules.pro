# Don't obfuscate to keep stack traces readable
-dontobfuscate

# Auto generated warnings
-dontwarn "org.bouncycastle.jsse.BCSSLParameters"
-dontwarn "org.bouncycastle.jsse.BCSSLSocket"
-dontwarn "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider"
-dontwarn "org.conscrypt.Conscrypt$Version"
-dontwarn "org.conscrypt.Conscrypt"
-dontwarn "org.conscrypt.ConscryptHostnameVerifier"
-dontwarn "org.openjsse.javax.net.ssl.SSLParameters"
-dontwarn "org.openjsse.javax.net.ssl.SSLSocket"
-dontwarn "org.openjsse.net.ssl.OpenJSSE"
-dontwarn "org.slf4j.impl.StaticLoggerBinder"

# Firebase finds its component registrars through the manifest and instantiates them by reflection;
# the consumer rule shipped before firebase-components 18.0.1 keeps the class but not the constructor
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# androidx.window references classes provided by the device at runtime
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
