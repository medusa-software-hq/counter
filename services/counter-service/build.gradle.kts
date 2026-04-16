plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.versionCatalogUpdate)
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-parameters")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
