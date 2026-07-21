plugins {
  application

  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.shadow)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
  implementation(libs.clikt)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.kotlin.test)
}

application {
  mainClass = "software.medusa.counter.cli.MainKt"

  // Clikt pulls in JNA (terminal detection); recent JDKs warn on its System.load unless native
  // access is opted in. Keep the installDist launcher quiet (the Homebrew launcher passes the
  // same).
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// Bake the CLI OAuth client secret + API base URL into the fat jar as a resource. The Publish CLI
// workflow passes them via `-PcliOauthClientSecret` / `-PcliApiBaseUrl` (from an Actions secret and
// variable). Absent locally → empty values, and Config falls back to env vars / the prod default,
// so dev builds still work. Neither value is ever committed.
val cliBuildConfigDir = layout.buildDirectory.dir("generated/cliBuildConfig")

val generateCliBuildConfig by tasks.registering {
  val clientSecret = providers.gradleProperty("cliOauthClientSecret").orElse("")
  val apiBaseUrl = providers.gradleProperty("cliApiBaseUrl").orElse("")
  inputs.property("clientSecret", clientSecret)
  inputs.property("apiBaseUrl", apiBaseUrl)
  outputs.dir(cliBuildConfigDir)
  doLast {
    val file = cliBuildConfigDir.get().file("counter-cli-build.properties").asFile
    file.parentFile.mkdirs()
    // Both values are properties-safe (a `GOCSPX-…` secret and an https URL — `:` and `/` are fine
    // in a value). Written by hand to avoid Properties.store's date comment.
    file.writeText("oauthClientSecret=${clientSecret.get()}\napiBaseUrl=${apiBaseUrl.get()}\n")
  }
}

sourceSets.named("main") { resources.srcDir(generateCliBuildConfig) }

tasks.shadowJar {
  archiveBaseName = "counter-cli"
  archiveClassifier = ""
  archiveVersion = ""
}

tasks.named("check") { dependsOn(tasks.named("ktfmtCheck")) }

detekt { config.setFrom(file("config/detekt/detekt.yml")) }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

tasks.withType<Test>().configureEach { useJUnitPlatform() }
