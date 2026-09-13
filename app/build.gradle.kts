import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("VERSION_CODE").get().toInt()

// Machine-local settings. local.properties is gitignored, which is why the signing key lives
// there and not in the build script — the key is what the phones trust.
val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun setting(localKey: String, envKey: String): String? =
    localConfig.getProperty(localKey) ?: System.getenv(envKey)

val signingKeystore = setting("fclick.keystore", "FCLICK_KEYSTORE")
    ?.let { file(it) }
    ?.takeIf { it.isFile }

// Where the app looks for its own updates. CI points this at the repository's latest release, so
// a build always knows where it came from. Empty means self-update is switched off.
val updateManifestUrl = setting("fclick.updateUrl", "FCLICK_UPDATE_URL").orEmpty()

android {
    namespace = "dev.todor.fassistantclick"
    compileSdk = 36
    // Pinned so CI installs exactly what has been built against locally.
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "dev.todor.fassistantclick"
        // dispatchGesture() does not exist below Android 7.0, and it is the whole app.
        minSdk = 24
        // Deliberately low, matching fassistant: no runtime notification permission and no
        // foreground-service type to declare. The install floor is 24, so this clears it by one.
        targetSdk = 25
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("upgrade") {
                storeFile = signingKeystore
                storePassword = setting("fclick.keystorePassword", "FCLICK_KEYSTORE_PASSWORD")
                keyAlias = setting("fclick.keyAlias", "FCLICK_KEY_ALIAS")
                keyPassword = setting("fclick.keyPassword", "FCLICK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Without the key a clone still builds and runs; it just cannot produce an APK that
        // upgrades an installed copy in place, because the signature will not match.
        val upgradeSigning = signingConfigs.findByName("upgrade")

        getByName("debug") {
            signingConfig = upgradeSigning ?: signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
            signingConfig = upgradeSigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets.getByName("main").kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * Produces everything a release needs: the APK under a versioned name for humans, a copy under a
 * stable name so an update URL never has to change, and the manifest the app polls.
 */
tasks.register("dist") {
    dependsOn("assembleRelease")
    val releaseOutputs = layout.buildDirectory.dir("outputs/apk/release")
    val distDir = rootProject.layout.projectDirectory.dir("dist")
    val changelog = rootProject.file("CHANGELOG.md")

    doLast {
        val built = releaseOutputs.get().asFile.listFiles { candidate -> candidate.extension == "apk" }
            ?.singleOrNull()
            ?: error("expected exactly one release APK in ${releaseOutputs.get().asFile}")

        val target = distDir.asFile.apply { mkdirs() }
        built.copyTo(File(target, "fassistant-click-$appVersionName-$appVersionCode.apk"), overwrite = true)
        built.copyTo(File(target, "fassistant-click.apk"), overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256").digest(built.readBytes())
            .joinToString("") { "%02x".format(it) }

        File(target, "update.json").writeText(
            """
            {
              "versionCode": $appVersionCode,
              "versionName": "$appVersionName",
              "apkUrl": "fassistant-click.apk",
              "sha256": "$digest",
              "notes": "${jsonEscape(releaseNotes(changelog))}"
            }
            """.trimIndent() + "\n"
        )

        logger.lifecycle("dist: $appVersionName ($appVersionCode), ${built.length() / 1024} KB, sha256 $digest")
    }
}

fun jsonEscape(text: String): String = buildString {
    for (character in text) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
        }
    }
}

/** The top section of the changelog, which becomes the release notes and the in-app update notes. */
fun releaseNotes(changelog: File): String {
    if (!changelog.isFile) return ""
    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return ""
    val rest = lines.drop(start + 1)
    val end = rest.indexOfFirst { it.startsWith("## ") }
    return (if (end < 0) rest else rest.take(end)).joinToString("\n").trim()
}
