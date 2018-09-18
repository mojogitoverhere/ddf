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
package org.codice.ddf.catalog.ui.imports;

import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class MetacardImportItem {

  private Metacard metacard;

  private ContentImportItem content;

  private Map<String, ContentImportItem> derivedContent = new HashMap<>();

  private List<MetacardImportItem> history = new LinkedList<>();

  Metacard getMetacard() {
    return metacard;
  }

  void setMetacard(Metacard metacard) {
    this.metacard = metacard;
  }

  ContentImportItem getContent() {
    return content;
  }

  void setContentImportItem(ContentImportItem content) {
    this.content = content;
  }

  Map<String, ContentImportItem> getDerivedContent() {
    return derivedContent;
  }

  List<MetacardImportItem> getHistory() {
    return history;
  }

  static class ContentImportItem {

    private BinaryContent binaryContent;

    private String fileName;

    ContentImportItem(BinaryContent binaryContent, String fileName) {
      this.binaryContent = binaryContent;
      this.fileName = fileName;
    }

    BinaryContent getBinaryContent() {
      return binaryContent;
    }

    String getFileName() {
      return fileName;
    }
  }
}
