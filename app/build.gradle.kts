plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

// Config di firma letta da release/keystore.properties (non tracciato) o da env vars
val keystorePropertiesFileCandidates = listOf(
    rootProject.file("release/keystore.properties"),
    rootProject.file("keystore.properties")
)
val keystorePropertiesFile = keystorePropertiesFileCandidates.firstOrNull { it.exists() }
    ?: keystorePropertiesFileCandidates.last()
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingProp(key: String, env: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

fun resolveSigningStoreFile(storePath: String): File =
    if (File(storePath).isAbsolute) {
        File(storePath)
    } else {
        keystorePropertiesFile.parentFile.resolve(storePath)
    }

fun hasSigningConfig(storePath: String?, storePass: String?, alias: String?, keyPass: String?): Boolean =
    storePath != null && storePass != null && alias != null && keyPass != null

fun gradleBooleanProperty(name: String): Boolean =
    providers.gradleProperty(name).orNull?.equals("true", ignoreCase = true) == true

fun shouldValidateNightlySigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleNightlyRelease",
        "bundleNightlyRelease",
        "packageNightlyRelease",
        "publishNightlyRelease",
        "installNightlyRelease"
    )
    return taskNames.any { task ->
        signingTaskHints.any { hint -> task.contains(hint, ignoreCase = true) }
    }
}

fun shouldValidateStableSigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleStableRelease",
        "bundleStableRelease",
        "packageStableRelease",
        "publishStableRelease",
        "installStableRelease"
    )
    return taskNames.any { task ->
        signingTaskHints.any { hint -> task.contains(hint, ignoreCase = true) }
    }
}

android {
    namespace = "it.palsoftware.pastiera"
    compileSdk = 36

    val defaultVersionCode = 86
    val defaultVersionName = "0.86"
    val ciVersionCode = providers.gradleProperty("PASTIERA_VERSION_CODE").orNull?.toIntOrNull()
    val ciVersionName = providers.gradleProperty("PASTIERA_VERSION_NAME").orNull
    val nightlyVersionCode = providers.gradleProperty("PASTIERA_NIGHTLY_VERSION_CODE").orNull?.toIntOrNull()
    val nightlyVersionNameSuffix = providers.gradleProperty("PASTIERA_NIGHTLY_VERSION_SUFFIX").orNull ?: "-nightly"
    val isFdroidBuild = gradleBooleanProperty("PASTIERA_FDROID_BUILD")
    val klipyApiKey = providers.gradleProperty("PASTIERA_KLIPY_API_KEY").orNull ?: ""

    defaultConfig {
        applicationId = "it.palsoftware.pastiera"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: defaultVersionCode
        versionName = ciVersionName ?: defaultVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "KLIPY_API_KEY", "\"${klipyApiKey.escapeForBuildConfig()}\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")

            // Only configure signing if all credentials are provided
            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                val resolvedStoreFile = resolveSigningStoreFile(storePath!!)
                storeFile = resolvedStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
        create("nightly") {
            val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
            val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
            val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
            val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")

            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                val resolvedStoreFile = resolveSigningStoreFile(storePath!!)
                storeFile = resolvedStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    flavorDimensions += "channel"

    productFlavors {
        create("stable") {
            dimension = "channel"
            manifestPlaceholders["appLabel"] = "Pastiera"
            manifestPlaceholders["imeLabel"] = "Pastiera"
            buildConfigField("String", "RELEASE_CHANNEL", "\"stable\"")
            buildConfigField("boolean", "IS_FDROID_BUILD", if (isFdroidBuild) "true" else "false")
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATE_CHECKS", if (isFdroidBuild) "false" else "true")
        }
        create("nightly") {
            dimension = "channel"
            applicationIdSuffix = ".nightly"
            if (nightlyVersionCode != null) {
                versionCode = nightlyVersionCode
            }
            versionNameSuffix = nightlyVersionNameSuffix
            manifestPlaceholders["appLabel"] = "Pastiera Nightly"
            manifestPlaceholders["imeLabel"] = "Pastiera Nightly"
            buildConfigField("String", "RELEASE_CHANNEL", "\"nightly\"")
            buildConfigField("boolean", "IS_FDROID_BUILD", if (isFdroidBuild) "true" else "false")
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATE_CHECKS", if (isFdroidBuild) "false" else "true")
            val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
            val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
            val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
            val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")
            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                signingConfig = signingConfigs.getByName("nightly")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only use signing config if it's properly configured
            val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")
            
            if (!isFdroidBuild && hasSigningConfig(storePath, storePass, alias, keyPass)) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Disable lint for release to avoid file lock issues
            isDebuggable = false
        }
    }
    
    // Validate signing config only when building release
    tasks.whenTaskAdded {
        if (!isFdroidBuild && name.equals("preStableReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (!shouldValidateStableSigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping stable signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("storeFile", "PASTIERA_KEYSTORE_PATH")
                val storePass = signingProp("storePassword", "PASTIERA_KEYSTORE_PASSWORD")
                val alias = signingProp("keyAlias", "PASTIERA_KEY_ALIAS")
                val keyPass = signingProp("keyPassword", "PASTIERA_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for release build. Define storeFile, storePassword, keyAlias e keyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PASTIERA_KEYSTORE_PATH, " +
                            "PASTIERA_KEYSTORE_PASSWORD, PASTIERA_KEY_ALIAS, PASTIERA_KEY_PASSWORD. " +
                            "Use -PPASTIERA_FDROID_BUILD=true only for the unsigned stable F-Droid release path."
                    )
                }
            }
        }
        if (name.equals("preNightlyReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (!shouldValidateNightlySigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping nightly signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("nightlyStoreFile", "PASTIERA_NIGHTLY_KEYSTORE_PATH")
                val storePass = signingProp("nightlyStorePassword", "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD")
                val alias = signingProp("nightlyKeyAlias", "PASTIERA_NIGHTLY_KEY_ALIAS")
                val keyPass = signingProp("nightlyKeyPassword", "PASTIERA_NIGHTLY_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for nightly build. Define nightlyStoreFile, nightlyStorePassword, nightlyKeyAlias e nightlyKeyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PASTIERA_NIGHTLY_KEYSTORE_PATH, " +
                            "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD, PASTIERA_NIGHTLY_KEY_ALIAS, PASTIERA_NIGHTLY_KEY_PASSWORD."
                    )
                }
            }
        }
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // Exclude legacy JSON base dictionaries; keep serialized .dict and user_defaults.json
            excludes += "assets/common/dictionaries/*_base.json"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // RecyclerView per performance ottimali nella griglia emoji
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Emoji2 per supporto emoji future-proof
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    // Kotlinx Serialization for dictionary optimization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.3")
    // Shizuku for ADB shell access
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.mockito:mockito-core:5.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
