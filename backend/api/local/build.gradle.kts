plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

dependencies { implementation(project(":api:shared")) }

application { mainClass = "software.medusa.counter.server.MainKt" }
