/*
 * Copyright 2024 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.waveservice.api.util

import org.wave.libwave.usernames.Username

val Username.nickname: String get() = username.split(Usernames.DELIMITER)[0]
val Username.discriminator: String get() = username.split(Usernames.DELIMITER)[1]
