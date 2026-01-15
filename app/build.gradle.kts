import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.protobuf)
  id("com.mikepenz.aboutlibraries.plugin.android")
}

aboutLibraries { collect { configPath = file("../config") } }

android {
  namespace = "io.github.sms2email.sms2email"
  compileSdk { version = release(36) }

  defaultConfig {
    applicationId = "io.github.sms2email.sms2email"
    minSdk = 23
    targetSdk = 36
    versionCode = 17
    versionName = "1.1.4"
    base.archivesName = "SMS2Email-v" + versionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
  buildFeatures { compose = true }

  packaging {
    resources {
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/NOTICE.md"
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobufJavaLite.get()}" }
  generateProtoTasks {
    all().forEach { task -> task.builtins { register("java") { option("lite") } } }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.datastore.proto)
  implementation(libs.protobuf.javalite)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  implementation(libs.android.mail)
  implementation(libs.android.activation)
  implementation(libs.androidx.appcompat)
  implementation(libs.aboutlibraries.core)
  implementation(libs.aboutlibraries.compose.m3)
  implementation(libs.androidx.compose.material.icons.extended)
}
