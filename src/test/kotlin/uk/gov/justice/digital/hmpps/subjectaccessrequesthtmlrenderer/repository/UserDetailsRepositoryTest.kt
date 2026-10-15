package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail

@DataJpaTest
class UserDetailsRepositoryTest @Autowired constructor(
  val userDetailsRepository: UserDetailsRepository,
) {

  @Test
  fun `findFirstByUsernameIgnoreCaseOrderByUsernameAsc returns user detail for valid username`() {
    val userDetail = UserDetail(username = "AZ123PO", lastName = "Smith")
    userDetailsRepository.save(userDetail)

    val foundUserDetail = userDetailsRepository.findFirstByUsernameIgnoreCaseOrderByUsernameAsc("AZ123PO")
    assertThat(foundUserDetail).isNotNull
    assertThat(foundUserDetail?.lastName).isEqualTo("Smith")
  }

  @Test
  fun `findFirstByUsernameIgnoreCaseOrderByUsernameAsc returns user detail for username with different case`() {
    val userDetail = UserDetail(username = "az123po", lastName = "Smith")
    userDetailsRepository.save(userDetail)

    val foundUserDetail = userDetailsRepository.findFirstByUsernameIgnoreCaseOrderByUsernameAsc("AZ123PO")
    assertThat(foundUserDetail).isNotNull
    assertThat(foundUserDetail?.lastName).isEqualTo("Smith")
  }

  @Test
  fun `findFirstByUsernameIgnoreCaseOrderByUsernameAsc returns first match when usernames differ only by case`() {
    userDetailsRepository.save(UserDetail(username = "AZ123PO", lastName = "Smith"))
    userDetailsRepository.save(UserDetail(username = "az123po", lastName = "Jones"))

    val foundUserDetail = userDetailsRepository.findFirstByUsernameIgnoreCaseOrderByUsernameAsc("Az123Po")
    assertThat(foundUserDetail).isNotNull
    assertThat(foundUserDetail?.username).isEqualTo("AZ123PO")
    assertThat(foundUserDetail?.lastName).isEqualTo("Smith")
  }

  @Test
  fun `findFirstByUsernameIgnoreCaseOrderByUsernameAsc returns null for invalid username`() {
    val foundUserDetail = userDetailsRepository.findFirstByUsernameIgnoreCaseOrderByUsernameAsc("INVALID_USERNAME")
    assertThat(foundUserDetail).isNull()
  }
}
