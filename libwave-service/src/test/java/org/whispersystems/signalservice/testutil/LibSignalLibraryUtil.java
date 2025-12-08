package org.whispersystems.waveservice.testutil;

import org.wave.libwave.internal.Native;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public final class LibWaveLibraryUtil {

  /**
   * Attempts to initialize the LibWave Native class, which will load the native binaries.
   * <p>
   * If that fails to link, then on Unix, it will fail as we rely on that for CI.
   * <p>
   * If that fails to link, and it's not Unix, it will skip the test via assumption violation.
   * <p>
   * If using inside a PowerMocked test, the assumption violation can be fatal, use:
   * {@code @PowerMockRunnerDelegate(JUnit4.class)}
   */
  public static void assumeLibWaveSupportedOnOS() {
    try {
      Class.forName(Native.class.getName());
    } catch (ClassNotFoundException e) {
      fail();
    } catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
      String osName = System.getProperty("os.name");

      if (isUnix(osName)) {
        fail("Not able to link native LibWave on a key OS: " + osName);
      } else {
        assumeNoException("Not able to link native LibWave on this operating system: " + osName, e);
      }
    }
  }

  private static boolean isUnix(String osName) {
    assertNotNull(osName);
    osName = osName.toLowerCase();
    return osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
  }
}
