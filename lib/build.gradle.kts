import java.nio.file.Paths

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.offlineinterpreter.lib"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            cppFlags += "-std=c++17 -fno-rtti"
            arguments += listOf(
                "-DANDROID_STL=c++_shared",
                "-DGGML_STATIC=OFF",
                "-DLLAMA_BUILD_LIB=ON",
                "-DLLAMA_BUILD_EXAMPLES=OFF",
                "-DLLAMA_BUILD_TESTS=OFF",
                "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DLLAMA_ACCELERATE=ON"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-android:1.3.3")
    implementation(libs.kotlinx.coroutines.core)
}

val localProperties = java.util.Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val sdkDir: String = localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_SDK_ROOT") ?: "/usr/local/lib/android/sdk"

val LLAMA_SRC: String = run {
    val env = System.getenv("LLAMA_SRC")
    if (env != null) env
    else {
        val default = rootProject.projectDir.parentFile.resolve("llama.cpp").absolutePath
        default
    }
}

android.sourceSets["main"] {
    java.srcDirs("src/main/java")
    jniLibs.srcDirs("src/main/jniLibs")
    val cppDir = file("src/main/cpp")
    val resolvedLlama = file(LLAMA_SRC)
    // CMake in externalNativeBuild reads CMakeLists.txt which references ${LLAMA_SRC}
    // We pass it via -DLLAMA_SRC=... in cmake args above
}
