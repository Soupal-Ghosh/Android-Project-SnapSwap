plugins {
    id("com.android.application") version "8.1.4" apply false
    id("com.android.library") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.21" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false  // ✅ add this
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}