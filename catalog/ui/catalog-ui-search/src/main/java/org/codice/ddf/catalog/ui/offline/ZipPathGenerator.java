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
package org.codice.ddf.catalog.ui.offline;

import java.nio.file.Paths;

public class ZipPathGenerator {

  private ZipPathGenerator() {}

  public static String metacardPath(String metacardId) {
    return Paths.get(rootPath(metacardId), "metacard", metacardId + ".xml").toString();
  }

  public static String contentPath(String metacardId, String contentFileName) {
    return Paths.get(rootPath(metacardId), "content", contentFileName).toString();
  }

  public static String derivedOverviewPath(String metacardId, String name) {
    return Paths.get(derivedRootPath(metacardId), "overview", name).toString();
  }

  public static String derivedOriginalPath(String metacardId, String name) {
    return Paths.get(derivedRootPath(metacardId), "original", name).toString();
  }

  public static String derivedOtherPath(String metacardId, int index, String name) {
    return Paths.get(derivedRootPath(metacardId), derivedOtherContentName(index), name).toString();
  }

  public static String derivedOtherContentName(int index) {
    return "other-" + index;
  }

  private static String rootPath(String metacardId) {
    return Paths.get("metacards", metacardId.substring(0, 3), metacardId).toString();
  }

  private static String derivedRootPath(String metacardId) {
    return Paths.get(rootPath(metacardId), "derived").toString();
  }
}
