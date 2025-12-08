/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.messages.multidevice

import java.io.InputStream

/**
 * Data needed to sync a device contact avatar to/from other devices.
 */
data class DeviceContactAvatar(val inputStream: InputStream, val length: Long, val contentType: String)
