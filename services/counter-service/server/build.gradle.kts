plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

dependencies {
  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.counter.server.MainKt" }
