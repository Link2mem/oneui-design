@file:Suppress("UNCHECKED_CAST")

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties // السطر المنقذ الذي يحل الأخطاء الحالية

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.rikka.refine) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

apply(from = "manifest.gradle")

fun String.toEnvVarStyle(): String =
    this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .uppercase()

/**
 * دالة جلب خصائص GitHub مع حماية ضد توقف البناء
 */
fun getGithubProperty(key: String): String {
    val githubProperties = Properties().apply {
        val file = rootProject.file("github.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    
    val result = githubProperties.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
    
    return result ?: if (key == "ghUsername") "Link2mem" else "no_token_provided"
}

val githubUsername = getGithubProperty("ghUsername")
val githubAccessToken = getGithubProperty("ghAccessToken")

allprojects {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
        
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
    }
}

subprojects {
    plugins.withId("com.android.base") {
        plugins.apply("dev.rikka.tools.refine")
        project.extensions.findByType(BaseExtension::class.java)?.apply {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            configurations.all {
                exclude(group = "androidx.core", module = "core")
                exclude(group = "androidx.core", module = "core-ktx")
                exclude(group = "androidx.customview", module = "customview")
                exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
                exclude(group = "androidx.drawerlayout", module = "drawerlayout")
                exclude(group = "androidx.viewpager2", module = "viewpager2")
                exclude(group = "androidx.viewpager", module = "viewpager")
                exclude(group = "androidx.appcompat", module = "appcompat")
                exclude(group = "androidx.fragment", module = "fragment")
                exclude(group = "androidx.preference", module = "preference")
                exclude(group = "androidx.recyclerview", module = "recyclerview")
                exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
                exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
                exclude(group = "com.google.android.material", module = "material")
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    val groupName = "io.github.tribalfs"

    plugins.whenPluginAdded {
        val isAndroidLibrary = javaClass.name == "com.android.build.gradle.LibraryPlugin"
        val isAndroidApp = javaClass.name == "com.android.build.gradle.AppPlugin"

        if (isAndroidLibrary || isAndroidApp) {
            val artifact = project.name
            val versionInfo =
                rootProject.extensions.extraProperties.get("versions_metadata") as? Map<String, List<Any>>
            val artifactVersionInfo = versionInfo?.get(artifact)

            val designVersion = versionInfo?.get("oneui-design")?.get(0)?.toString() ?: "1.0.0"

            extensions.findByType(BaseExtension::class.java)?.apply {
                if (artifactVersionInfo != null) {
                    defaultConfig.versionName = artifactVersionInfo[0].toString()
                    compileSdkVersion((artifactVersionInfo[2] as Number).toInt())
                    defaultConfig.minSdk = (artifactVersionInfo[1] as Number).toInt()
                    defaultConfig.targetSdk = (artifactVersionInfo[2] as Number).toInt()
                } else {
                    compileSdkVersion(35)
                    defaultConfig.minSdk = 26
                    defaultConfig.targetSdk = 35
                }
                
                buildFeatures.buildConfig = true
                defaultConfig.versionCode = 1

                when (this) {
                    is AppExtension -> {
                        defaultConfig.buildConfigField(
                            "String",
                            "ONEUI_DESIGN_VERSION",
                            "\"$designVersion\""
                        )
                    }
                    is LibraryExtension -> {
                        publishing {
                            singleVariant("release") {
                                withSourcesJar()
                                withJavadocJar()
                            }
                        }
                    }
                }
            }

            afterEvaluate {
                if (!plugins.hasPlugin("maven-publish")) return@afterEvaluate

                extensions.findByType(PublishingExtension::class.java)?.apply {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            version = designVersion
                            groupId = groupName
                            artifactId = artifact
                            from(components.findByName("release"))

                            pom {
                                name.set(artifact)
                                url.set("https://github.com/tribalfs/oneui-design")
                            }
                        }
                    }
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
                            credentials {
                                username = githubUsername
                                password = githubAccessToken
                            }
                        }
                    }
                }
            }
        }
    }
}
