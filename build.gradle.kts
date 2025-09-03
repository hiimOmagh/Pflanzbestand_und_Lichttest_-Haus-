// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

// Ensure `gradlew test` runs the unit tests of the app module
tasks.register("test") {
    dependsOn(":app:testDebugUnitTest")
}