plugins {
  alias(libs.plugins.jib)
  alias(libs.plugins.kotlin.jvm)

  application
}

val javaVersion = 21

val containerPort = 8080
val containerImageRef = findProperty("jib.imageRef")?.toString() ?: "counter-service"
val containerImageTag = findProperty("jib.imageTag")?.toString() ?: "local"

val localRun: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
  runtimeClasspath += output + compileClasspath
}

dependencies {
  implementation(platform(libs.armeria.bom))

  implementation(libs.armeria.kotlin)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.nimbus.jose.jwt)
  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.counter.server.MainKt" }

tasks.register<JavaExec>("runLocal") {
  group = "application"
  description = "Run the server locally with no auth and localhost-only CORS"
  classpath = localRun.runtimeClasspath
  mainClass = "software.medusa.counter.server.LocalMainKt"
}

jib {
  from { image = "eclipse-temurin:$javaVersion-jre-alpine" }

  to {
    image = containerImageRef
    tags = setOf(containerImageTag)
  }

  container {
    ports = listOf(containerPort.toString())
    mainClass = "software.medusa.counter.server.MainKt"
  }
}
