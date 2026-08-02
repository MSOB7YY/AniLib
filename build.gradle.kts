// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins{
    kotlin("android") version "1.9.24" apply false
}


buildscript {
    repositories {
        google()
        mavenCentral()
        maven (url = "https://jitpack.io")
        maven (url = "https://maven.aliyun.com/repository/public")
        maven (url = "https://maven.aliyun.com/repository/central")
    }
    dependencies {
        classpath ("com.google.gms:google-services:4.3.15")
        classpath ("com.google.firebase:firebase-crashlytics-gradle:2.5.2")
        // 8.2 is the first AGP whose JdkImageTransform can drive JDK 21's jlink; 8.0.x fails
        // with "Error while executing process jlink.exe ... --disable-plugin system-modules"
        classpath("com.android.tools.build:gradle:8.2.2")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // jcenter is dead, and it hosted the only copies of com.otaliastudios:elements and
        // com.thefuntasty.hauler:core. Huawei's mirror still serves both, poms and aars.
        // It must come before the aliyun mirrors: aliyun has hauler's pom but 404s on the
        // aar, and Gradle does not fall back to another repo once it picks one.
        maven (url = "https://repo.huaweicloud.com/repository/maven")
        maven (url = "https://jitpack.io")
        maven (url = "https://maven.aliyun.com/repository/public")
        maven (url = "https://maven.aliyun.com/repository/central")
    }
}

tasks{
    register("clean", Delete::class) {
        delete(rootProject.buildDir)
    }
}
