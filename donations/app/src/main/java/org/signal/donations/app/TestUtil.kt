package org.wave.donations.app

import org.wave.donations.GooglePayApi

object TestUtil : GooglePayApi.Gateway {
  override fun getTokenizationSpecificationParameters(): Map<String, String> {
    return mapOf(
      "gateway" to "example",
      "gatewayMerchantId" to "exampleMerchantId"
    )
  }

  override val allowedCardNetworks: List<String> = listOf(
    "AMEX",
    "DISCOVER",
    "INTERAC",
    "JCB",
    "MASTERCARD",
    "VISA"
  )
}
