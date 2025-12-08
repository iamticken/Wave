package org.whispersystems.waveservice.internal.push.http;

import org.whispersystems.waveservice.api.crypto.DigestingOutputStream;
import org.whispersystems.waveservice.api.crypto.NoCipherOutputStream;

import java.io.OutputStream;

/**
 * See {@link NoCipherOutputStream}.
 */
public final class NoCipherOutputStreamFactory implements OutputStreamFactory {

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) {
    return new NoCipherOutputStream(wrap);
  }
}
