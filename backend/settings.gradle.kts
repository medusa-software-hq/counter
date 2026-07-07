plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "backend"

include("api:shared")
include("api:local")
include("api:gcp")
