plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

val pomGroupId = providers.gradleProperty("POM_GROUP_ID").orElse("io.github.huann305")
val pomVersion = providers.gradleProperty("POM_VERSION").orElse("1.0.0")
val pomUrl = providers.gradleProperty("POM_URL")
    .orElse("https://github.com/huann305/NotificationHelper")
val pomScmUrl = providers.gradleProperty("POM_SCM_URL")
    .orElse("https://github.com/huann305/NotificationHelper")

android {
    namespace = "com.huann305.notificationhelper.fullscreen.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 27
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":notificationhelper"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.glide)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = pomGroupId.get()
                artifactId = "notificationhelper-fullscreen"
                version = pomVersion.get()

                pom {
                    name.set("NotificationHelper FullScreen")
                    description.set(
                        "Optional Android full-screen lock-screen notification helper built on NotificationHelper."
                    )
                    url.set(pomUrl.get())
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("huann305")
                            name.set("huann305")
                        }
                    }
                    scm {
                        connection.set("scm:git:${pomScmUrl.get()}.git")
                        developerConnection.set("scm:git:${pomScmUrl.get()}.git")
                        url.set(pomScmUrl.get())
                    }
                }
            }
        }
        repositories {
            maven {
                name = "localBuild"
                url = layout.buildDirectory.dir("repo").get().asFile.toURI()
            }
        }
    }
}
