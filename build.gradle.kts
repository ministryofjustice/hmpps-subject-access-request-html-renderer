plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.2.0"
  kotlin("plugin.spring") version "2.2.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

// Required to fix issue with aws.sdk.kotlin:s3 that causes tests to hang.
ext["kotlin-coroutines.version"] = "1.10.2"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.8.2")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
  implementation("org.springframework.boot:spring-boot-starter-mustache")
  implementation("com.github.spullara.mustache.java:compiler:0.9.14")
  implementation("com.github.jknack:handlebars:4.5.0")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("aws.sdk.kotlin:s3:1.5.90")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.56.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-subject-access-request-lib:1.2.0")

  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("com.h2database:h2:2.4.240")
  runtimeOnly("org.postgresql:postgresql:42.7.8")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.8.2")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.36") {
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
  private var noData: Boolean = false

  @Option(
    option = "service",
    description = "The service name to generate report html for e.g 'hmpps-book-secure-move-api'",
  )
  fun setServiceName(serviceName: String) {
    this.serviceName = serviceName
  }

  @Option(
    option = "noData",
    description = "If set will generate report with no data for the selected service",
  )
  fun setServiceName() {
    this.noData = true
  }

  @Input
  fun getServiceName(): String = serviceName

  @Input
  fun isNoData(): Boolean = noData

  @TaskAction
  fun generate() {
    args(this.serviceName, this.noData)
    super.exec()
  }
}
