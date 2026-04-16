// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.1.1" apply false
}

buildscript {
    dependencies {
        // For KGP
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")

        // For KSP
//        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.20")
    }
}