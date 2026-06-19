plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("androidx.room") version "2.6.1" apply false
}

// Dependency locking for reproducible builds. Resolved dependency versions are
// pinned in per-module `gradle.lockfile` files (regenerate with `--write-locks`).
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
