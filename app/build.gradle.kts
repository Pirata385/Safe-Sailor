import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Assinatura do release: lida de keystore.properties (local, não versionado) ou de
// variáveis de ambiente (GitHub Actions). Sem nenhuma das duas, usa a chave de debug
// apenas para permitir testes locais — nunca distribua um APK assim.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")

android {
    namespace = "com.safesailor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safesailor"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.2"
    }

    // Duas edições do mesmo app (mesmo pacote e assinatura):
    // - padrao: instala normalmente a partir da internet.
    // - completa: inclui a proteção contra desinstalação (serviço de acessibilidade), que o
    //   Google Play Protect bloqueia ao instalar APKs baixados; requer desativá-lo na instalação.
    flavorDimensions += "edicao"
    productFlavors {
        create("padrao") {
            dimension = "edicao"
            isDefault = true
            buildConfigField("boolean", "UNINSTALL_GUARD", "false")
        }
        create("completa") {
            dimension = "edicao"
            versionNameSuffix = "-completa"
            buildConfigField("boolean", "UNINSTALL_GUARD", "true")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Safe Sailor: chave de release não configurada; assinando com a chave de debug.")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
