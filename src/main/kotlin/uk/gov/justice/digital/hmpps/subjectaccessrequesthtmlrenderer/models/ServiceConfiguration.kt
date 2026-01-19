package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "SERVICE_CONFIGURATION")
data class ServiceConfiguration(
  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(name = "service_name", nullable = false)
  var serviceName: String,

  @Column(name = "label", nullable = false)
  var label: String,

  @Column(name = "url", nullable = false)
  var url: String,

  @Column(name = "list_order", nullable = false)
  var order: Int,

  @Column(name = "enabled", nullable = false)
  var enabled: Boolean,

  @Column(name = "template_migrated", nullable = false)
  var templateMigrated: Boolean,

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  val category: ServiceCategory,
) {
  constructor() : this(
    id = UUID.randomUUID(),
    serviceName = "",
    label = "",
    url = "",
    order = 0,
    enabled = false,
    templateMigrated = false,
    category = ServiceCategory.PRISON,
  )
}

enum class ServiceCategory {
  PRISON,
  PROBATION,
}
