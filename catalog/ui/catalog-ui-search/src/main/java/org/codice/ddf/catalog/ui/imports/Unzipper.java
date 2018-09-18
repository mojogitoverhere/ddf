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
import java.util.concurrent.TimeUnit;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.progress.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Unzipper {

  private static final long ONE_HUNDRED_PERCENT = 100;

  private static final long JOIN_TIMEOUT_IN_MS = TimeUnit.SECONDS.toMillis(10);

  private static final long UNZIP_STATUS_LOOP_TIME_IN_MS = TimeUnit.SECONDS.toMillis(1);

  private static final Logger LOGGER = LoggerFactory.getLogger(Unzipper.class);

  Path unzip(ZipFile zipFile, Status status) throws ImportException {
    final ProgressMonitor progressMonitor = zipFile.getProgressMonitor();

    Thread t = createUnzipStatusThread(status, progressMonitor);

    File temporaryDirectory = Files.createTempDir();

    try {
      initializeStatus(status);
      t.start();
      zipFile.extractAll(temporaryDirectory.getAbsolutePath());

    } catch (ZipException e) {
      throw new ImportException(
          String.format(
              "Failed to extract zip file: path=[%s]", zipFile.getFile().getAbsolutePath()),
          e);
    } finally {
      t.interrupt();
      try {
        t.join(JOIN_TIMEOUT_IN_MS);
      } catch (InterruptedException e) {
        LOGGER.debug("Interrupted while waiting for unzip status thread to finish.", e);
        Thread.currentThread().interrupt();
      }
    }

    finalizeStatus(status);

    return Paths.get(temporaryDirectory.toURI());
  }

  private void finalizeStatus(Status status) {
    status.update(ONE_HUNDRED_PERCENT, ONE_HUNDRED_PERCENT);
  }

  private void initializeStatus(Status status) {
    status.update(0, ONE_HUNDRED_PERCENT);
  }

  private Thread createUnzipStatusThread(Status unzipStatus, ProgressMonitor progressMonitor) {
    return new Thread(
        () -> {
          while (!Thread.currentThread().isInterrupted()) {
            long completed = progressMonitor.getPercentDone();
            unzipStatus.update(completed, ONE_HUNDRED_PERCENT);
            try {
              Thread.sleep(UNZIP_STATUS_LOOP_TIME_IN_MS);
            } catch (InterruptedException e) {
              LOGGER.debug("Exiting progress monitor loop for unzipping file to import.", e);
              Thread.currentThread().interrupt();
              return;
            }
          }
        });
  }
}
