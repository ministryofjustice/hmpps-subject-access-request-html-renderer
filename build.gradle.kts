plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.3"
  kotlin("plugin.spring") version "2.1.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.3.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.4.0")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("aws.sdk.kotlin:s3:1.4.38")

  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.3.232")
  runtimeOnly("org.postgresql:postgresql:42.7.5")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.3.1")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}

tasks.register<TemplateGenerator>("generateReport") {
  group = "templates"
  description = "Generate subject access report HTML for the specified service name"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass = "uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templateGenerator.TemplateDevelopmentUtilKt"
  environment = mapOf("TEST_RESOURCES_DIR" to project.rootDir.resolve("src/test/resources"))
}

abstract class TemplateGenerator : JavaExec() {
  private lateinit var serviceName: String

  @Option(
    option = "service",
    description = "The service name to generate report html for e.g 'hmpps-book-secure-move-api'",
  )
  fun setServiceName(serviceName: String) {
    this.serviceName = serviceName
    args(serviceName)
  }

  @Input
  fun getServiceName(): String = serviceName
}
