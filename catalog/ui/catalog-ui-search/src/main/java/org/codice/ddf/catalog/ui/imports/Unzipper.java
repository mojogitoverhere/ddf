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

import com.google.common.io.Files;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Unzipper {

  private static final Logger LOGGER = LoggerFactory.getLogger(Unzipper.class);

  Path unzip(ZipFile zipFile) throws ImportException {

    File temporaryDirectory = Files.createTempDir();

    try {
      LOGGER.debug("Extracting file to {}", temporaryDirectory.getAbsolutePath());
      zipFile.extractAll(temporaryDirectory.getAbsolutePath());
    } catch (ZipException e) {
      throw new ImportException(
          String.format(
              "Failed to extract zip file: path=[%s]", zipFile.getFile().getAbsolutePath()),
          e);
    }
    return Paths.get(temporaryDirectory.toURI());
  }
}
