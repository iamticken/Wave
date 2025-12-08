package org.whispersystems.waveservice.api.push.exceptions

/**
 * Exception representing that the submitted information was not accepted (e.g. the push challenge token or captcha did not match)
 */
class TokenNotAcceptedException : NonSuccessfulResponseCodeException(403)
