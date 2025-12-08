package org.whispersystems.waveservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an individual credential for an archive operation. Note that is isn't the final
 * credential you will actually use -- that's [org.wave.libwave.zkgroup.backups.BackupAuthCredential].
 * But you use these to make those.
 */
class ArchiveServiceCredential(
  @JsonProperty
  val credential: ByteArray,
  @JsonProperty
  val redemptionTime: Long
)
