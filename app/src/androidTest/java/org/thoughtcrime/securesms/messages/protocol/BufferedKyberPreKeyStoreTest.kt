/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages.protocol

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.wave.core.models.ServiceId
import org.wave.libwave.protocol.ReusedBaseKeyException
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.testing.WaveDatabaseRule
import org.thoughtcrime.securesms.util.KyberPreKeysTestUtil

class BufferedKyberPreKeyStoreTest {

  @get:Rule
  val harness = WaveDatabaseRule()

  private lateinit var aci: ServiceId
  private lateinit var testSubject: BufferedKyberPreKeyStore
  private lateinit var dataStore: BufferedWaveServiceAccountDataStore

  @Before
  fun setUp() {
    WaveStore.account.generateAciIdentityKeyIfNecessary()

    aci = harness.localAci
    testSubject = BufferedKyberPreKeyStore(aci)
    dataStore = BufferedWaveServiceAccountDataStore(aci)
  }

  @Test
  fun givenALastResortKey_whenIMarkKyberPreKeyUsed_thenIExpectNoIssues() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test(expected = ReusedBaseKeyException::class)
  fun givenALastResortKey_whenIMarkKyberPreKeyUsedTwice_thenIExpectException() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test
  fun givenAMarkedLastResortKey_whenIFlushTwice_thenIExpectNoIssues() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.flushToDisk(dataStore)
    testSubject.flushToDisk(dataStore)
  }
}
