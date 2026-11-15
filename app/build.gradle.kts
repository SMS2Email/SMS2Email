import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.protobuf)
  id("com.mikepenz.aboutlibraries.plugin.android")
}

aboutLibraries { collect { configPath = file("../config") } }

androidComponents {
  beforeVariants { variant ->
    if (variant.buildType == "release") {
      variant.javaClass.methods
          .firstOrNull { it.name == "setEnableUnitTest" }
          ?.invoke(variant, true)
    }
  }
}

android {
  namespace = "io.github.sms2email.sms2email"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "io.github.sms2email.sms2email"
    minSdk = 23
    targetSdk = 37
    versionCode = 53
    versionName = "1.1.40"
    base.archivesName = "SMS2Email-v" + versionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      // Matrix SDK 1.6.62's reflective Moshi adapters fail under R8.
      isMinifyEnabled = false
      isShrinkResources = false
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

  // Keep release/device builds small; add x86_64 only for local emulator testing with -Pemulator.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      if (providers.gradleProperty("emulator").isPresent) include("x86_64")
      isUniversalApk = false
    }
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }

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
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.datastore.proto)
  implementation(libs.protobuf.javalite)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  implementation(libs.android.mail)
  implementation(libs.android.activation)
  implementation(libs.matrix.android.sdk)
  implementation(libs.aboutlibraries.core)
  implementation(libs.aboutlibraries.compose.m3)
  implementation(libs.androidx.compose.material.icons.extended)
}
