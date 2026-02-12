// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  id("com.mikepenz.aboutlibraries.plugin.android") version "13.2.1" apply false
}
