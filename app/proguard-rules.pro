# Add project-specific ProGuard rules here.
# For Phase 0 the release build is not minified, so this file is unused.

# --- RetroAchievements (rcheevos JNI bridge) ---
# Native code resolves these members by name via JNI; never rename or strip
# them, even if minification is enabled in a future release build.
-keep class br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni {
    *;
}
-keep class br.com.redclaw.zelda64player.retroachievements.jni.RaNativeListener {
    *;
}
