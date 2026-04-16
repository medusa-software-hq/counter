import com.google.protobuf.gradle.id

plugins {
  alias(libs.plugins.jib)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.protobuf)

  application
}

val javaVersion = 21

val containerPort = 8080
val containerImageRef = findProperty("jib.imageRef")?.toString() ?: "counter-service"
val containerImageTag = findProperty("jib.imageTag")?.toString() ?: "local"

sourceSets { main { proto { srcDir("../../../proto") } } }

dependencies {
  implementation(platform(libs.armeria.bom))
  implementation(platform(libs.grpc.bom))

  implementation(libs.armeria.grpc)
  implementation(libs.armeria.grpc.kotlin)
  implementation(libs.armeria.kotlin)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.guava)
  implementation(libs.protobuf.kotlin)
  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.counter.server.MainKt" }

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

val grpcJavaId = "grpc"
val grpcKotlinId = "grpckt"

protobuf {
  protoc { artifact = "${libs.protobuf.protoc.get()}" }

  plugins {
    id(grpcJavaId) { artifact = "${libs.protobuf.protocGen.grpc.java.get()}" }
    id(grpcKotlinId) { artifact = "${libs.protobuf.protocGen.grpc.kotlin.get()}:jdk8@jar" }
  }

  generateProtoTasks {
    all().forEach { protoTask ->
      protoTask.plugins {
        id(grpcJavaId)
        id(grpcKotlinId)
      }

      protoTask.builtins { id("kotlin") }
    }
  }
}
