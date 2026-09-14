plugins {
    kotlin("jvm") version "2.4.10"
    application
}

application {
    mainClass.set("lk.fincore.MainKt")
}

group = "lk.fincore"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(26)
}

tasks.test {
    useJUnitPlatform()
}