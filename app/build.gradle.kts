import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  id("com.google.gms.google-services")
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

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("release") {
      if (project.hasProperty("releaseKeyStore")) {
        storeFile = file(project.property("releaseKeyStore") as String)
        storePassword = project.property("releaseKeyStorePassword") as String
        keyAlias = project.property("releaseKeyAlias") as String
        keyPassword = project.property("releaseKeyPassword") as String
      }
    }
  }

  buildTypes {
    named("release") {
      if (project.hasProperty("releaseKeyStore")) {
        signingConfig = signingConfigs.getByName("release")
      }
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
      signingConfig = null
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-0"
    }

    named("debug") {
      signingConfig = signingConfigs.getByName("debugConfig")
      applicationIdSuffix = ".debug"
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
  implementation(libs.lottie.compose)

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
