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

import ddf.catalog.data.Metacard;
import java.nio.file.Paths;

class ZipPathGenerator {

  private final String parentMetacardId;

  ZipPathGenerator(String parentMetacardId) {
    this.parentMetacardId = parentMetacardId;
  }

  String primaryMetacardPath() {
    return Paths.get(rootPath(parentMetacardId), "metacard", parentMetacardId + ".xml").toString();
  }

  String historyMetacardPath(Metacard metacard) {
    return Paths.get(historyRootPath(metacard.getId()), "metacard", metacard.getId() + ".xml")
        .toString();
  }

  String historyContentPath(String id, String name) {
    return Paths.get(historyRootPath(id), "content", name).toString();
  }

  String primaryContentPath(String metacardId, String contentFileName) {
    return Paths.get(rootPath(metacardId), "content", contentFileName).toString();
  }

  String derivedOverviewContentPath(String name) {
    return Paths.get(derivedRootPath(), "overview", name).toString();
  }

  String derivedOriginalContentPath(String name) {
    return Paths.get(derivedRootPath(), "original", name).toString();
  }

  String derivedOtherContentPath(int intex, String name) {
    return Paths.get(derivedRootPath(), derivedOtherContentName(intex), name).toString();
  }

  String historyDerivedOriginalContentPath(String metacardId, String name) {
    return Paths.get(rootPath(parentMetacardId), "history", metacardId, "derived", "original", name)
        .toString();
  }

  String historyDerivedOverviewContentPath(String metacardId, String name) {
    return Paths.get(rootPath(parentMetacardId), "history", metacardId, "derived", "overview", name)
        .toString();
  }

  String historyDerivedOtherContentPath(String metacardId, int index, String name) {
    return Paths.get(
            rootPath(parentMetacardId),
            "history",
            metacardId,
            "derived",
            derivedOtherContentName(index),
            name)
        .toString();
  }

  private String derivedOtherContentName(int index) {
    return "other-" + index;
  }

  private String rootPath(String metacardId) {
    return Paths.get("metacards", metacardId.substring(0, 3), metacardId).toString();
  }

  private String historyRootPath(String metacardId) {
    return Paths.get(rootPath(parentMetacardId), "history", metacardId).toString();
  }

  private String derivedRootPath() {
    return Paths.get(rootPath(parentMetacardId), "derived").toString();
  }
}
