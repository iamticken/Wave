/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.groupsv2

import org.wave.core.models.ServiceId
import org.wave.core.models.ServiceId.ACI
import org.wave.storageservice.protos.groups.local.DecryptedMember
import org.wave.storageservice.protos.groups.local.DecryptedPendingMember
import org.wave.storageservice.protos.groups.local.DecryptedRequestingMember
import java.util.Optional

fun Collection<DecryptedMember>.toAciListWithUnknowns(): List<ACI> {
  return DecryptedGroupUtil.toAciListWithUnknowns(this)
}

fun Collection<DecryptedMember>.toAciList(): List<ACI> {
  return DecryptedGroupUtil.toAciList(this)
}

fun Collection<DecryptedMember>.findMemberByAci(aci: ACI): Optional<DecryptedMember> {
  return DecryptedGroupUtil.findMemberByAci(this, aci)
}

fun Collection<DecryptedRequestingMember>.findRequestingByAci(aci: ACI): Optional<DecryptedRequestingMember> {
  return DecryptedGroupUtil.findRequestingByAci(this, aci)
}

fun Collection<DecryptedPendingMember>.findPendingByServiceId(serviceId: ServiceId): Optional<DecryptedPendingMember> {
  return DecryptedGroupUtil.findPendingByServiceId(this, serviceId)
}
