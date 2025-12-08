package org.thoughtcrime.securesms.crypto.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.wave.libwave.protocol.IdentityKey
import org.wave.libwave.protocol.WaveProtocolAddress
import org.wave.libwave.protocol.ecc.ECPublicKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord
import org.whispersystems.waveservice.test.LibWaveLibraryUtil.assumeLibWaveSupportedOnOS

class WaveBaseIdentityKeyStoreTest {
  companion object {
    private const val ADDRESS = "address1"
  }

  @Before
  fun ensureNativeSupported() {
    assumeLibWaveSupportedOnOS()
  }

  @Test
  fun `getIdentity() hits disk on first retrieve but not the second`() {
    val mockDb = mockk<IdentityTable>()
    val subject = WaveBaseIdentityKeyStore(mockk<Context>(), mockDb)
    val identityKey = IdentityKey(ECPublicKey.fromPublicKeyBytes(ByteArray(32)))
    val record = mockRecord(ADDRESS, identityKey)

    every { mockDb.getIdentityStoreRecord(ADDRESS) } returns record

    assertEquals(identityKey, subject.getIdentity(WaveProtocolAddress(ADDRESS, 1)))
    verify(exactly = 1) { mockDb.getIdentityStoreRecord(ADDRESS) }

    assertEquals(identityKey, subject.getIdentity(WaveProtocolAddress(ADDRESS, 1)))
    verify(exactly = 1) { mockDb.getIdentityStoreRecord(ADDRESS) }
  }

  @Test
  fun `invalidate() evicts cache entry`() {
    val mockDb = mockk<IdentityTable>()
    val subject = WaveBaseIdentityKeyStore(mockk<Context>(), mockDb)
    val identityKey = IdentityKey(ECPublicKey.fromPublicKeyBytes(ByteArray(32)))
    val record = mockRecord(ADDRESS, identityKey)

    every { mockDb.getIdentityStoreRecord(ADDRESS) } returns record

    assertEquals(identityKey, subject.getIdentity(WaveProtocolAddress(ADDRESS, 1)))
    verify(exactly = 1) { mockDb.getIdentityStoreRecord(ADDRESS) }

    subject.invalidate(ADDRESS)

    assertEquals(identityKey, subject.getIdentity(WaveProtocolAddress(ADDRESS, 1)))
    verify(exactly = 2) { mockDb.getIdentityStoreRecord(ADDRESS) }
  }

  private fun mockRecord(addressName: String, identityKey: IdentityKey): IdentityStoreRecord {
    return IdentityStoreRecord(
      addressName = addressName,
      identityKey = identityKey,
      verifiedStatus = IdentityTable.VerifiedStatus.DEFAULT,
      firstUse = false,
      timestamp = 1,
      nonblockingApproval = true
    )
  }
}
