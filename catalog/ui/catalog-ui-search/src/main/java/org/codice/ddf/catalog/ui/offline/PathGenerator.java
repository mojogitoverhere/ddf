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

import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generate the relative destination path for an offline resource. */
class PathGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(PathGenerator.class);

  String generatePath(String root, String metacardId) throws IOException {
    createParentDirectories(root + File.separator + metacardId, metacardId);
    return metacardId;
  }

  private void createParentDirectories(String outputPath, String metacardId) throws IOException {
    File parentFile = new File(outputPath).getParentFile();

    boolean mkdirResults = parentFile.mkdirs();
    if (!mkdirResults) {
      LOGGER.trace(
          "Failed to make the parent directories. This may be because they already exist: path=[{}]",
          parentFile);
    }

    if (!parentFile.exists()) {
      throw new IOException(
          String.format(
              "Unable to create parent directories while offlining a resource: directory=[%s] metacardId=[%s]",
              parentFile, metacardId));
    }
  }
}
