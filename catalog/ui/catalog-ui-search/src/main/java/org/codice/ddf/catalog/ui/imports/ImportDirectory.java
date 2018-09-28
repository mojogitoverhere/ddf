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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.platform.util.InputValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportDirectory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportDirectory.class);

  private String rootDirectory;

  public List<Path> getPaths() {
    if (StringUtils.isBlank(rootDirectory)) {
      return Collections.emptyList();
    }

    try (Stream<Path> allPaths = Files.walk(Paths.get(rootDirectory), 1)) {
      return allPaths
          .filter(this::isValidPath)
          .sorted(this::compareIgnoreCase)
          .collect(Collectors.toList());
    } catch (IOException e) {
      LOGGER.debug("Unable to walk through root import directory [{}]", rootDirectory, e);
      return Collections.emptyList();
    }
  }

  private boolean isValidPath(Path path) {
    return isValidFile(path) || isValidDirectory(path);
  }

  private boolean isValidFile(Path path) {
    return path.toFile().isFile()
        && !path.toFile().isHidden()
        && !InputValidation.isBadFile(path.toString());
  }

  private boolean isValidDirectory(Path path) {
    return path.toFile().isDirectory() && !path.equals(Paths.get(rootDirectory));
  }

  private int compareIgnoreCase(Path path1, Path path2) {
    return path1.toString().compareToIgnoreCase(path2.toString());
  }

  @Override
  public String toString() {
    return rootDirectory == null ? "" : rootDirectory;
  }

  public void setRootDirectory(String rootDirectory) {
    if (rootDirectory == null) {
      return;
    }

    // Strip trailing slashes
    this.rootDirectory = Paths.get(rootDirectory).toString();
  }
}
