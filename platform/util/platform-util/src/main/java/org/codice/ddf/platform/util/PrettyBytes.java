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

public class PrettyBytes {

  private static final long BYTES_IN_ONE_KB = 1024;

  private static final long BYTES_IN_ONE_MB = BYTES_IN_ONE_KB * 1024;

  private static final long BYTES_IN_ONE_GB = BYTES_IN_ONE_MB * 1024;

  private static final long BYTES_IN_ONE_TB = BYTES_IN_ONE_GB * 1024;

  public static String prettify(long numBytes) {
    if (numBytes < 0) {
      throw new IllegalArgumentException("The number of bytes must be a positive number");
    }

    String prettySize;
    if (numBytes >= BYTES_IN_ONE_TB) {
      double numTB = numBytes / (double) BYTES_IN_ONE_TB;
      prettySize = String.format("%1$.2f TB", numTB);
    } else if (numBytes >= BYTES_IN_ONE_GB) {
      double numGB = numBytes / (double) BYTES_IN_ONE_GB;
      prettySize = String.format("%1$.2f GB", numGB);
    } else if (numBytes >= BYTES_IN_ONE_MB) {
      double numMB = numBytes / (double) BYTES_IN_ONE_MB;
      prettySize = String.format("%1$.2f MB", numMB);
    } else if (numBytes >= BYTES_IN_ONE_KB) {
      double numKB = numBytes / (double) BYTES_IN_ONE_KB;
      prettySize = String.format("%1$.2f KB", numKB);
    } else {
      prettySize = String.format("%d B", numBytes);
    }
    return prettySize;
  }

  private PrettyBytes() {}
}
