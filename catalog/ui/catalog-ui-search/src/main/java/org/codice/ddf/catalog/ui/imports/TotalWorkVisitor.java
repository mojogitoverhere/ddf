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

import java.nio.file.Path;

class TotalWorkVisitor implements ExtractedArchive.Visitor {

  private int count = 0;

  int getCount() {
    return count;
  }

  @Override
  public void visitMetacardXml(Path metacardXmlFile) {
    count++;
  }

  @Override
  public void visitContent(Path contentFile) {}

  @Override
  public void visitDerivedContent(String type, Path derivedContentFile) {}

  @Override
  public void visitHistoryMetacardXml(Path metacardXmlFile) {}

  @Override
  public void visitHistoryDerivedContent(String type, Path derivedContentFile) {}

  @Override
  public void visitHistoryContent(Path contentFile) {}
}
