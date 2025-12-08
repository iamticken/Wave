package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class WaveProxyUtilText_convertUserEnteredAddressToHost {

  private final String input;
  private final String output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "https://wave.tube/#proxy.parker.org",     "proxy.parker.org" },
        { "https://wave.tube/#proxy.parker.org:443", "proxy.parker.org" },
        { "sgnl://wave.tube/#proxy.parker.org",      "proxy.parker.org" },
        { "sgnl://wave.tube/#proxy.parker.org:443",  "proxy.parker.org" },
        { "proxy.parker.org",                          "proxy.parker.org" },
        { "proxy.parker.org:443",                      "proxy.parker.org" },
        { "x",                                         "x" },
        { "",                                          "" }
    });
  }

  public WaveProxyUtilText_convertUserEnteredAddressToHost(String input, String output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    assertEquals(output, WaveProxyUtil.convertUserEnteredAddressToHost(input));
  }
}
