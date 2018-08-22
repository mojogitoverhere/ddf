/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.platform.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

public class PrettyBytesTest {

  @Test(expected = IllegalArgumentException.class)
  public void testPrettifyNegativeNumberThrowsIllegalArgumentException() {
    PrettyBytes.prettify(-123);
  }

  @Test
  public void testPrettifyZeroBytes() {
    assertThat(PrettyBytes.prettify(0), is("0 B"));
  }

  @Test
  public void testPrettifyLessThanOneKB() {
    assertThat(PrettyBytes.prettify(500), is("500 B"));
  }

  @Test
  public void testPrettifyOneKB() {
    assertThat(PrettyBytes.prettify(1024), is("1.00 KB"));
  }

  @Test
  public void testPrettifyBetweenOneKBAndOneMB() {
    assertThat(PrettyBytes.prettify((long) (1024 * 3.14)), is("3.14 KB"));
  }

  @Test
  public void testPrettifyOneMB() {
    assertThat(PrettyBytes.prettify((long) (1024 * 1024)), is("1.00 MB"));
  }

  @Test
  public void testPrettifyBetweenOneMBAndOneGB() {
    assertThat(PrettyBytes.prettify((long) (1024 * 1024 * 3.14)), is("3.14 MB"));
  }

  @Test
  public void testPrettifyOneGB() {
    assertThat(PrettyBytes.prettify((long) (1024 * 1024 * 1024)), is("1.00 GB"));
  }

  @Test
  public void testPrettifyBetweenOneGBAndOneTB() {
    assertThat(PrettyBytes.prettify((long) (1024 * 1024 * 1024 * 3.14)), is("3.14 GB"));
  }

  @Test
  public void testPrettifyOneTB() {
    // We get a numeric overflow if we cast the value after the calculation
    // so we cast one of the values first
    assertThat(PrettyBytes.prettify(1024 * 1024 * 1024 * 1024L), is("1.00 TB"));
  }

  @Test
  public void testPrettifyGreaterThanOneTB() {
    // We get a numeric overflow if we cast the value after the calculation
    // so we cast the calculation with the double first
    assertThat(PrettyBytes.prettify(1024 * 1024 * 1024 * (long) (1024 * 3.14)), is("3.14 TB"));
  }
}
