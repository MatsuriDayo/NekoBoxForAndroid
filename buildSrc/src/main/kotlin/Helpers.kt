import com.android.build.gradle.AbstractAppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import java.security.MessageDigest
import java.util.*
import kotlin.system.exitProcess

fun sha256Hex(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

private val Project.android get() = extensions.getByName<BaseExtension>("android")

private lateinit var metadata: Properties
private lateinit var localProperties: Properties
private lateinit var flavor: String

fun Project.requireFlavor(): String {
    if (::flavor.isInitialized) return flavor
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        val taskName = gradle.startParameter.taskNames[0]
        when {
            taskName.contains("assemble") -> {
                flavor = taskName.substringAfter("assemble")
                return flavor
            }
            taskName.contains("install") -> {
                flavor = taskName.substringAfter("install")
                return flavor
            }
            taskName.contains("bundle") -> {
                flavor = taskName.substringAfter("bundle")
                return flavor
            }
        }
    }

    flavor = ""
    return flavor
}

fun Project.requireMetadata(): Properties {
    if (!::metadata.isInitialized) {
        metadata = Properties().apply {
            load(rootProject.file("nb4a.properties").inputStream())
        }
    }
    return metadata
}

fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()

        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {

            localProperties.load(Base64.getDecoder().decode(base64).inputStream())
        } else if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(rootProject.file("local.properties").inputStream())
        }
    }
    return localProperties
}

fun Project.requireTargetAbi(): String {
    var targetAbi = ""
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        if (gradle.startParameter.taskNames.size == 1) {
            val targetTask = gradle.startParameter.taskNames[0].toLowerCase(Locale.ROOT).trim()
            when {
                targetTask.contains("arm64") -> targetAbi = "arm64-v8a"
                targetTask.contains("arm") -> targetAbi = "armeabi-v7a"
                targetTask.contains("x64") -> targetAbi = "x86_64"
                targetTask.contains("x86") -> targetAbi = "x86"
            }
        }
    }
    return targetAbi
}

fun Project.setupCommon() {
    android.apply {
        buildToolsVersion("30.0.3")
        compileSdkVersion(33)
        defaultConfig {
            minSdk = 21
            targetSdk = 33
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        (android as ExtensionAware).extensions.getByName<KotlinJvmOptions>("kotlinOptions").apply {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
        lintOptions {
            isShowAll = true
            isCheckAllWarnings = true
            isCheckReleaseBuilds = false
            isWarningsAsErrors = true
            textOutput = project.file("build/lint.txt")
            htmlOutput = project.file("build/lint.html")
        }
        packagingOptions {
            excludes.addAll(
                listOf(
                    "**/*.kotlin_*",
                    "/META-INF/*.version",
                    "/META-INF/native/**",
                    "/META-INF/native-image/**",
                    "/META-INF/INDEX.LIST",
                    "DebugProbesKt.bin",
                    "com/**",
                    "org/**",
                    "**/*.java",
                    "**/*.proto",
                    "okhttp3/**"
                )
            )
        }
        packagingOptions {
            jniLibs.useLegacyPackaging = true
        }
        (this as? AbstractAppExtension)?.apply {
            buildTypes {
                getByName("release") {
                    isShrinkResources = true
                    if (System.getenv("nkmr_minify") == "0") {
                        isShrinkResources = false
                        isMinifyEnabled = false
                    }
                }
                getByName("debug") {
                    applicationIdSuffix = "debug"
                    debuggable(true)
                    jniDebuggable(true)
                }
            }
            applicationVariants.forEach { variant ->
                variant.outputs.forEach {
                    it as BaseVariantOutputImpl
                    it.outputFileName = it.outputFileName.replace(
                        "app", "${project.name}-" + variant.versionName
                    ).replace("-release", "").replace("-oss", "")
                }
            }
        }
    }
}

fun Project.setupAppCommon() {
    setupCommon()

    val lp = requireLocalProperties()
    val keystorePwd = lp.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = lp.getProperty("ALIAS_NAME") ?: System.getenv("ALIAS_NAME")
    val pwd = lp.getProperty("ALIAS_PASS") ?: System.getenv("ALIAS_PASS")

    android.apply {
        if (keystorePwd != null) {
            signingConfigs {
                create("release") {
                    storeFile(rootProject.file("release.keystore"))
                    storePassword(keystorePwd)
                    keyAlias(alias)
                    keyPassword(pwd)
                }
            }
        } else if (requireFlavor().contains("(Oss|Expert|Play)Release".toRegex())) {
            exitProcess(0)
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                if (requireTargetAbi().isBlank()) {
                    getByName("release").signingConfig = key
                }
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupApp() {
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME")
    val verName = requireMetadata().getProperty("VERSION_NAME")
    val verCode = (requireMetadata().getProperty("VERSION_CODE").toInt()) * 5
    android.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
        }
    }
    setupAppCommon()

    val targetAbi = requireTargetAbi()

    android.apply {
        this as AbstractAppExtension

        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro")
                )
            }
        }

        splits.abi {
            isEnable = true
            isUniversalApk = false
            if (targetAbi.isNotBlank()) {
                reset()
                include(targetAbi)
            }
        }

        flavorDimensions("vendor")
        productFlavors {
            create("oss")
            create("fdroid")
            create("play")
        }

        applicationVariants.all {
            outputs.all {
                this as BaseVariantOutputImpl
                outputFileName = outputFileName.replace(project.name, "NB4A-$versionName")
                    .replace("-release", "")
                    .replace("-oss", "")
            }
        }

        for (abi in listOf("Arm64", "Arm", "X64", "X86")) {
            tasks.create("assemble" + abi + "FdroidRelease") {
                dependsOn("assembleFdroidRelease")
            }
        }

    }
}