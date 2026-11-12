plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
}

tasks.register("testAll") {
    dependsOn(":core:test", ":host:test", ":client:test", ":sdk:test")
    description = "Run JVM unit tests for core, host, client, and sdk."
}
