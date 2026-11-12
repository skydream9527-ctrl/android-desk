plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sdk"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
