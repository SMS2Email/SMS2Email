const {
  KEYSTORE_FILE,
  KEYSTORE_STORE_PASSWORD,
  KEYSTORE_KEY_ALIAS,
  KEYSTORE_KEY_PASSWORD,
} = process.env;

module.exports = {
  plugins: [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    "@semantic-release/changelog",
    [
      "@semantic-release/exec",
      {
        // prepareCmd design:
        // - Runs during semantic-release "prepare" to update repo files + produce the signed APK artifact.
        // - Keeps ${nextRelease.version} / ${nextRelease.notes} as *semantic-release placeholders* (they are not JS).
        // - Reads KEYSTORE_* once via JS (process.env) and injects the concrete values into the Gradle command.
        // - Updates app/build.gradle.kts (versionCode++, versionName = "<nextRelease.version>").
        // - Writes Fastlane/Play changelog file: metadata/en-US/changelogs/<versionCode>.txt with nextRelease notes.
        // - Builds: ./gradlew :app:assembleRelease using injected signing properties.
        prepareCmd: [
          "sed -i -E 's/versionCode = [0-9]+/versionCode = $(( $(grep -oP \"versionCode = \\\\K[0-9]+\" app/build.gradle.kts) + 1 ))/' app/build.gradle.kts",
          "sed -i -E 's/versionName = .*/versionName = \"${nextRelease.version}\"/' app/build.gradle.kts",
          "mkdir -p metadata/en-US/changelogs",
          'echo "${nextRelease.notes}" > metadata/en-US/changelogs/$(grep -oP "versionCode = \\K[0-9]+" app/build.gradle.kts).txt',
          "./gradlew :app:assembleRelease \\",
          `  -Pandroid.injected.signing.store.file="${KEYSTORE_FILE}" \\`,
          `  -Pandroid.injected.signing.store.password="${KEYSTORE_STORE_PASSWORD}" \\`,
          `  -Pandroid.injected.signing.key.alias="${KEYSTORE_KEY_ALIAS}" \\`,
          `  -Pandroid.injected.signing.key.password="${KEYSTORE_KEY_PASSWORD}"`,
        ].join("\n"),
      },
    ],
    [
      "@semantic-release/git",
      {
        assets: [
          "CHANGELOG.md",
          "app/build.gradle.kts",
          "metadata/en-US/changelogs/*.txt",
        ],
      },
    ],
    [
      "@semantic-release/github",
      {
        assets: [
          { path: "app/build/outputs/apk/release/*.apk", label: "Android APK" },
        ],
      },
    ],
  ],
};
