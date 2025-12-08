/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.wave.core.util

import java.util.UUID

fun UUID.toByteArray(): ByteArray = UuidUtil.toByteArray(this)
