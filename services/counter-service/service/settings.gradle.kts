plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// 🎨 TEMPLATE EJECT: Rename the root project
rootProject.name = "counter-service"

include("shared")
include("local")
include("gcp")
