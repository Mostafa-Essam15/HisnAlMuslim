plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.hisn.muslim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hisn.muslim"
        minSdk = 26
        targetSdk = 35
        // رقم الإصدار بيزيد مع كل بناء على GitHub عشان التحديث فوق النسخة القديمة يشتغل
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 2
        versionName = "2.0"
    }

    // مفتاح توقيع ثابت: كل النسخ بنفس التوقيع فتتثبّت فوق بعض من غير ما تفقد بياناتك.
    // (مفتاح تجريبي للتوزيع الشخصي؛ لنسخة متجر يُستخدم مفتاح خاص غير منشور.)
    signingConfigs {
        create("hisn") {
            storeFile = file("hisn-debug.jks")
            storePassword = "hisnapp123"
            keyAlias = "hisn"
            keyPassword = "hisnapp123"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("hisn") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // حساب مواقيت الصلاة
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    // إشعارات فورية بالتحديثات (Firebase Cloud Messaging)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
}
