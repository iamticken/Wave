package org.thoughtcrime.securesms.groups.v2

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.rootCause
import okio.ByteString
import org.junit.Test
import org.wave.core.util.Base64.encodeUrlSafeWithoutPadding
import org.wave.libwave.zkgroup.InvalidInputException
import org.wave.storageservice.protos.groups.GroupInviteLink
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl.InvalidGroupLinkException
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl.UnknownGroupLinkVersionException
import org.thoughtcrime.securesms.util.Util
import java.io.IOException

@Suppress("ClassName")
class GroupInviteLinkUrl_InvalidGroupLinkException_Test {
  @Test
  fun empty_string() {
    val uri = ""
    assertThat(GroupInviteLinkUrl.fromUri(uri)).isNull()
  }

  @Test
  fun not_a_url_string() {
    val uri = "abc"
    assertThat(GroupInviteLinkUrl.fromUri(uri)).isNull()
  }

  @Test
  fun wrong_host() {
    val uri = "https://x.wave.org/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"
    assertThat(GroupInviteLinkUrl.fromUri(uri)).isNull()
  }

  @Test
  fun wrong_scheme() {
    val uri = "http://wave.group/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"
    assertThat(GroupInviteLinkUrl.fromUri(uri)).isNull()
  }

  @Test
  fun has_path() {
    val uri = "https://wave.group/not_expected/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"
    assertFailure { GroupInviteLinkUrl.fromUri(uri) }
      .isInstanceOf<InvalidGroupLinkException>()
      .hasMessage("No path was expected in uri")
  }

  @Test
  fun missing_ref() {
    val uri = "https://wave.group/"
    assertFailure { GroupInviteLinkUrl.fromUri(uri) }
      .isInstanceOf<InvalidGroupLinkException>()
      .hasMessage("No reference was in the uri")
  }

  @Test
  fun empty_ref() {
    val uri = "https://wave.group/#"
    assertFailure { GroupInviteLinkUrl.fromUri(uri) }
      .isInstanceOf<InvalidGroupLinkException>()
      .hasMessage("No reference was in the uri")
  }

  @Test
  fun bad_base64() {
    val uri = "https://wave.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCX;mwVi31USVY"
    assertFailure { GroupInviteLinkUrl.fromUri(uri) }
      .isInstanceOf<InvalidGroupLinkException>()
      .rootCause()
      .isInstanceOf<IOException>()
  }

  @Test
  fun bad_protobuf() {
    val uri = "https://wave.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"
    assertFailure {
      GroupInviteLinkUrl.fromUri(uri)
    }.isInstanceOf<InvalidGroupLinkException>()
      .rootCause()
      .isInstanceOf<IllegalStateException>()
  }

  @Test
  fun version_999_url() {
    val url = "https://wave.group/#uj4zCiDMSxlNUvF4bQ3z3fYzGyZTFbJ1xEqWbPE3uZSD8bjOrxIP8NxV-0GUz3jpxMLR1rN3"
    assertFailure { GroupInviteLinkUrl.fromUri(url) }
      .isInstanceOf<UnknownGroupLinkVersionException>()
      .messageContains("Url contains no known group link content")
  }

  @Test
  fun bad_master_key_length() {
    val masterKeyBytes = Util.getSecretBytes(33)
    val password = GroupLinkPassword.createNew()

    val encoding = createEncodedProtobuf(masterKeyBytes, password.serialize())

    val url = "https://wave.group/#$encoding"

    assertFailure { GroupInviteLinkUrl.fromUri(url) }
      .isInstanceOf<InvalidGroupLinkException>()
      .rootCause()
      .isInstanceOf<InvalidInputException>()
  }

  companion object {
    private fun createEncodedProtobuf(
      groupMasterKey: ByteArray,
      passwordBytes: ByteArray
    ): String {
      return encodeUrlSafeWithoutPadding(
        GroupInviteLink.Builder()
          .v1Contents(
            GroupInviteLink.GroupInviteLinkContentsV1.Builder()
              .groupMasterKey(ByteString.of(*groupMasterKey))
              .inviteLinkPassword(ByteString.of(*passwordBytes))
              .build()
          )
          .build()
          .encode()
      )
    }
  }
}
