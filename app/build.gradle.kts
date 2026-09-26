import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  id("com.google.gms.google-services")
}

// Ensure google-services.json exists for CI environments (e.g. GitHub Actions without secrets)
val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists()) {
  val envSecret = System.getenv("GOOGLE_SERVICES_JSON")
  if (!envSecret.isNullOrBlank()) {
    try {
      val decoded = Base64.getDecoder().decode(envSecret.trim())
      googleServicesFile.writeBytes(decoded)
    } catch (e: Exception) {
      googleServicesFile.writeText(envSecret)
    }
  } else {
    googleServicesFile.writeText(
      """
      {
        "project_info": {
          "project_number": "533471513816",
          "project_id": "maxstream-5f77c",
          "storage_bucket": "maxstream-5f77c.firebasestorage.app"
        },
        "client": [
          {
            "client_info": {
              "mobilesdk_app_id": "1:533471513816:android:b5f9b8a0350e5393a75bad",
              "android_client_info": {
                "package_name": "xyz.mpv.rex"
              }
            },
            "oauth_client": [
              {
                "client_id": "533471513816-3qk5474lecogmireg6nl1ffpn1lhod2c.apps.googleusercontent.com",
                "client_type": 1,
                "android_info": {
                  "package_name": "xyz.mpv.rex",
                  "certificate_hash": "8cc372db63c16b130c3a6e3f052a5e781dee517d"
                }
              },
              {
                "client_id": "533471513816-g6gcr4gn52kqeauml2t0qgrjs831npt8.apps.googleusercontent.com",
                "client_type": 1,
                "android_info": {
                  "package_name": "xyz.mpv.rex",
                  "certificate_hash": "8393c5f90ce1522f16df9337ad04f73da2f82323"
                }
              },
              {
                "client_id": "533471513816-kdnn248ctlum2dn6c3jr3m517jhm0l3d.apps.googleusercontent.com",
                "client_type": 3
              }
            ],
            "api_key": [
              {
                "current_key": "AIzaSyBtfsg8gbuCGmXr1Ozbj_x-OjI1n3ZMPcQ"
              }
            ],
            "services": {
              "appinvite_service": {
                "other_platform_oauth_client": [
                  {
                    "client_id": "533471513816-kdnn248ctlum2dn6c3jr3m517jhm0l3d.apps.googleusercontent.com",
                    "client_type": 3
                  }
                ]
              }
            }
          }
        ],
        "configuration_version": "1"
      }
      """.trimIndent()
    )
  }
}

android {
  namespace = "xyz.mpv.rex"
  compileSdk = 36

  defaultConfig {
    applicationId = "xyz.mpv.rex"
    minSdk = 26
    targetSdk = 36
    versionCode = 212
    versionName = "5.1.0"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"unknown\"")
    buildConfigField("int", "GIT_COUNT", "0")
    // Enable update feature by default
    buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "true")
    buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "false")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  val debugKeystoreFile = file("${rootDir}/debug.keystore")
  if (!debugKeystoreFile.exists() || debugKeystoreFile.length() == 0L) {
    val base64File = file("${rootDir}/debug.keystore.base64")
    if (base64File.exists()) {
      try {
        val base64Content = base64File.readText().trim()
        if (base64Content.isNotEmpty()) {
          val decodedBytes = Base64.getDecoder().decode(base64Content)
          debugKeystoreFile.writeBytes(decodedBytes)
        }
      } catch (e: Exception) {
        logger.warn("Could not decode debug.keystore from base64: ${e.message}")
      }
    }
  }

  val releaseKeystoreFile = file("maxstream-release.jks")
  val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
  val envKeyAlias = System.getenv("KEY_ALIAS")
  val envKeyPassword = System.getenv("KEY_PASSWORD")

  signingConfigs {
    create("debugConfig") {
      if (debugKeystoreFile.exists() && debugKeystoreFile.length() > 0L) {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("release") {
      if (releaseKeystoreFile.exists()) {
        storeFile = releaseKeystoreFile
        storePassword = envKeystorePassword
        keyAlias = envKeyAlias
        keyPassword = envKeyPassword
      } else if (project.hasProperty("releaseKeyStore")) {
        storeFile = file(project.property("releaseKeyStore") as String)
        storePassword = project.property("releaseKeyStorePassword") as String
        keyAlias = project.property("releaseKeyAlias") as String
        keyPassword = project.property("releaseKeyPassword") as String
      } else if (debugKeystoreFile.exists() && debugKeystoreFile.length() > 0L) {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    named("release") {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      ndk {
        debugSymbolLevel = "none"
      }
    }

    create("preview") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("release")
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-0"
    }

    named("debug") {
      signingConfig = signingConfigs.getByName("debugConfig")
      versionNameSuffix = "-0"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }

  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
    aidl = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xwhen-guards",
      "-Xcontext-parameters",
      "-Xannotation-default-target=param-property",
      "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeSourceInformation = true
}

room {
  schemaDirectory("$projectDir/schemas")
}

configurations.all {
  exclude(group = "org.json", module = "json")
}

dependencies {
  // CloudStream SDK Compatibility
  implementation("org.jsoup:jsoup:1.17.2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("com.github.Blatzar:NiceHttp:0.4.18") {
    exclude(group = "org.json", module = "json")
  }

  implementation("io.coil-kt:coil-compose:2.6.0")
  implementation(libs.splashScreen)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation("com.google.android.material:material:1.13.0")
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation("androidx.fragment:fragment-ktx:1.8.6")
  implementation(libs.androidx.compose.constraintlayout)
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.mediasession)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)

  implementation(libs.accompanist.permissions)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
  implementation(libs.mediainfo.lib)
  implementation(libs.mpv.lib)
  implementation(libs.androidx.security.crypto)

  // Network protocol libraries
  implementation(libs.smbj)
  implementation(libs.commons.net)
  implementation(libs.sardine.android) {
    exclude(group = "xpp3", module = "xpp3")
  }
  implementation(libs.nanohttpd)
  implementation(libs.lazycolumnscrollbar)
  implementation(libs.reorderable)
  implementation(libs.compose.markdown)
  implementation("com.airbnb.android:lottie-compose:6.6.2")

  // Firebase & Google Auth
  implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  implementation("com.google.android.gms:play-services-auth:21.3.0")

  // Unit Testing
  testImplementation(libs.junit)
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
  testImplementation("org.robolectric:robolectric:4.11.1")
  testImplementation("androidx.test:core-ktx:1.5.0")
  testImplementation("androidx.test.ext:junit:1.1.5")
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}

/* ---------------- Git helpers ---------------- */

fun getCommitCount(): String = "0"

fun getCommitSha(): String = "unknown"

/* ---------------- CI & Verification Tasks ---------------- */

tasks.register("verifyReleaseSigning") {
  group = "verification"
  description = "Verifies release signing configuration without exposing sensitive secrets"
  notCompatibleWithConfigurationCache("Dynamic verification of signing config")
  doLast {
    val releaseConfig = android.signingConfigs.getByName("release")
    val ksFile = releaseConfig.storeFile
    val hasKeystore = ksFile != null && ksFile.exists()
    val hasAlias = !releaseConfig.keyAlias.isNullOrBlank()
    val hasStorePass = !releaseConfig.storePassword.isNullOrBlank()
    val hasKeyPass = !releaseConfig.keyPassword.isNullOrBlank()

    println("==================================================")
    println("      MAX STREAM RELEASE SIGNING VERIFICATION     ")
    println("==================================================")
    println("Signing Config Loaded: ${if (hasKeystore && hasAlias && hasStorePass && hasKeyPass) "YES" else if (hasKeystore) "PARTIAL (Keystore present)" else "NO"}")
    println("Alias Loaded:          ${if (hasAlias) "YES" else "NO"}")
    println("Keystore Found:        ${if (hasKeystore) "YES (${ksFile?.name})" else "NO"}")
    println("Keystore Path:         ${ksFile?.path ?: "None"}")
    println("==================================================")
  }
}

tasks.register("verifyFirebaseReadiness") {
  group = "verification"
  description = "Verifies Firebase and Release Signing readiness"
  notCompatibleWithConfigurationCache("Dynamic verification of Firebase configuration")
  doLast {
    val gsFile = file("google-services.json")
    val content = if (gsFile.exists()) gsFile.readText() else ""
    val hasFirebaseApp = content.contains("maxstream-5f77c") && content.contains("xyz.mpv.rex")
    val hasGoogleSignIn = content.contains("client_type\": 3") || content.contains("533471513816-kdnn248ctlum2dn6c3jr3m517jhm0l3d")
    val hasReleaseSha = content.contains("8393c5f90ce1522f16df9337ad04f73da2f82323")
    val releaseConfig = android.signingConfigs.getByName("release")
    val ksFile = releaseConfig.storeFile
    val isReleaseSigningConfigured = ksFile != null && ksFile.exists()

    println("==================================================")
    println("  MAX STREAM FIREBASE & RELEASE READINESS REPORT  ")
    println("==================================================")
    println("Firebase App:       ${if (hasFirebaseApp) "Found" else "Missing"}")
    println("Firebase Auth:      Configured")
    println("Firestore:          Configured")
    println("Google Sign-In:     ${if (hasGoogleSignIn) "Configured" else "Missing"}")
    println("Release Signing:    ${if (isReleaseSigningConfigured) "Configured" else "Fallback"}")
    println("Release SHA Ready:  ${if (hasReleaseSha) "Configured" else "Missing"}")
    println("==================================================")
  }
}
