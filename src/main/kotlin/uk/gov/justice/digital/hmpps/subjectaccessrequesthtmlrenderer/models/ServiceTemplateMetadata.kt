package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "SERVICE_TEMPLATE_METADATA")
data class ServiceTemplateMetadata(

  @Id
  val id: UUID = UUID.randomUUID(),

  @ManyToOne
  @JoinColumn(name = "service_configuration_id")
  val serviceConfiguration: ServiceConfiguration? = null,

  // TODO add constraint on service_id & version
  @Column(name = "version", nullable = false)
  val version: Int,

  @Column(name = "created", nullable = false)
  val created: LocalDateTime = LocalDateTime.now(),
) {
  constructor() : this(UUID.randomUUID(), null, 0)
}
