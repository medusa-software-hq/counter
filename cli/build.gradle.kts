import com.google.protobuf.gradle.id

plugins {
  application

  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.shadow)
}

// The CounterService proto lives at the repo root, shared with the backend; the CLI generates a
// blocking gRPC stub from the same contract the server implements.
sourceSets { main { proto { srcDir(rootDir.resolve("proto")) } } }

dependencies {
  implementation(libs.clikt)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.grpc.bom))
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  // protoc (4.34.x) generates against a matching protobuf-java; grpc-protobuf otherwise drags in an
  // older 3.x runtime that lacks the generated code's symbols (RuntimeVersion, GeneratedMessage).
  implementation(libs.protobuf.java)
  runtimeOnly(libs.grpc.okhttp)

  testImplementation(libs.kotlin.test)
}

val grpcJavaId = "grpc"

protobuf {
  protoc { artifact = "${libs.protobuf.protoc.get()}" }
  plugins { id(grpcJavaId) { artifact = "${libs.protobuf.protocGen.grpc.java.get()}" } }
  generateProtoTasks { all().forEach { it.plugins { id(grpcJavaId) } } }
}

application {
  mainClass = "software.medusa.counter.cli.MainKt"

  // Clikt pulls in JNA (terminal detection); recent JDKs warn on its System.load unless native
  // access is opted in. Keep the installDist launcher quiet (the Homebrew launcher passes the
  // same).
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.shadowJar {
  archiveBaseName = "counter-cli"
  archiveClassifier = ""
  archiveVersion = ""
  // gRPC discovers its transport/name-resolver/load-balancer providers via META-INF/services;
  // merge those files so the shaded jar keeps a functional channel provider.
  mergeServiceFiles()
}
