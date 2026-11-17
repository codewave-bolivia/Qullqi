import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.browser)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.codewave.qullqi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.codewave.qullqi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        // Backend base URL per build (spec 004, research R6). Override in ~/.gradle/gradle.properties,
        // e.g. qullqi.api.url.debug=http://<lan-ip>:8080/api/v1/ for a physical device.
        debug {
            val url = providers.gradleProperty("qullqi.api.url.debug").getOrElse("http://10.0.2.2:8080/api/v1/")
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
        }
        release {
            val url = providers.gradleProperty("qullqi.api.url.release").getOrElse("https://api.qullqi.invalid/api/v1/")
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
// Every activity in the final app — ours and those added by libraries — must be locked to
// portrait (spec 003, FR-035). Fails the build instead of shipping a landscape surprise.
// Note: Android 16+ ignores the lock on large screens (≥ 600 dp, targetSdk 37: no opt-out).
listOf("debug", "release").forEach { variant ->
    val cap = variant.replaceFirstChar { it.uppercase() }
    val verify = tasks.register("verifyPortraitActivities$cap") {
        group = "verification"
        description = "Fails if any activity in the merged $variant manifest is not screenOrientation=portrait."
        dependsOn("process${cap}MainManifest")
        val manifest = layout.buildDirectory.file("intermediates/merged_manifest/$variant/process${cap}MainManifest/AndroidManifest.xml")
        inputs.file(manifest)
        doLast {
            val xml = manifest.get().asFile.readText()
            val activities = Regex("<activity\\b[^>]*>").findAll(xml).map { it.value }.toList()
            val notPortrait = activities
                .filterNot { it.contains("android:screenOrientation=\"portrait\"") }
                .map { Regex("android:name=\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: it }
            if (notPortrait.isNotEmpty()) {
                throw GradleException("Activities not locked to portrait (FR-035): ${notPortrait.joinToString()}")
            }
            logger.lifecycle("verifyPortraitActivities$cap: ${activities.size} activities, all portrait")
        }
    }
    tasks.matching { it.name == "assemble$cap" }.configureEach { dependsOn(verify) }
}
tasks.register("verifyPortraitActivities") {
    group = "verification"
    dependsOn("verifyPortraitActivitiesDebug", "verifyPortraitActivitiesRelease")
}
