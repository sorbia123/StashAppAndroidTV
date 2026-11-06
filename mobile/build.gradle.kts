import com.android.build.api.dsl.ApplicationExtension
import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    alias(libs.plugins.apollo)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.protobuf)
}

kotlin {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        jvmTarget = JvmTarget.JVM_21
        javaParameters = true
    }
}

android {
    namespace = "com.github.damontecres.stashapp.mobile"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.github.damontecres.stashapp.mobile"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

apollo {
    service("mobile") {
        packageName.set("com.github.damontecres.stashapp.mobile.api")
        schemaFile = File("$projectDir/src/main/graphql/schema.graphqls")
        srcDir("src/main/graphql")
        generateOptionalOperationVariables.set(false)
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.protobuf.kotlin.lite.get().version}"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java") {
                    option("lite")
                }
            }
            it.builtins {
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    
    // Dependencies to satisfy compilation of shared :app sources
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)
    implementation(libs.androidx.leanback.tab)

    implementation(libs.glide)
    implementation(libs.glide.okhttp3.integration)
    ksp(libs.glide.ksp)

    implementation(libs.android.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidsvg.aar)
    implementation(kotlin("reflect"))

    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.apollo.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.previewseekbar)
    implementation(libs.previewseekbar.media3)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.zoomlayout)
    implementation(libs.compose.wheel.picker)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markwon.core)
    implementation(libs.parcelable.core)

    implementation(libs.acra.http)
    implementation(libs.acra.dialog)
    implementation(libs.acra.limiter)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.compose.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.window.size)
    debugImplementation(libs.androidx.ui.tooling)
    
    implementation(libs.androidx.ui.viewbinding)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cachecontrol)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
    implementation(libs.navigation.reimagined)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.restring)
    implementation(libs.viewpump)
    implementation(libs.reword)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.m3)

    implementation(libs.timber)
    implementation(libs.slf4j2.timber)

    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.truth)
}
