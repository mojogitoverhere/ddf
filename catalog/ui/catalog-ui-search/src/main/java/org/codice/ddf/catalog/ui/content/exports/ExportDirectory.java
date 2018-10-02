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
package org.codice.ddf.catalog.ui.content.exports;

import java.io.File;
import java.nio.file.Paths;
import org.codice.ddf.configuration.PropertyResolver;
import spark.utils.StringUtils;

public class ExportDirectory {

  private String exportDirectory = System.getProperty("ddf.home");

  public void setExportDirectory(String exportDirectory) {
    this.exportDirectory =
        StringUtils.isBlank(exportDirectory)
            ? System.getProperty("ddf.home")
            : PropertyResolver.resolveProperties(exportDirectory);
  }

  @Override
  public String toString() {
    return exportDirectory;
  }

  public boolean existsAndIsWritable() {
    if (StringUtils.isBlank(exportDirectory)) {
      return false;
    }

    File exportRoot = Paths.get(exportDirectory).toFile();
    return exportRoot.isDirectory() && exportRoot.canWrite();
  }
}
