plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("testAll") {
    dependsOn(":core:test", ":host:test", ":client:test")
    description = "Run JVM unit tests for core, host, and client."
}
