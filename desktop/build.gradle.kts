plugins {
    kotlin("jvm") version "2.0.21"
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.shoshiluna.desktop.MainKt")
    applicationName = "ShoshiLunaDesktop"
}

base {
    archivesName.set("shoshi-luna-desktop")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
