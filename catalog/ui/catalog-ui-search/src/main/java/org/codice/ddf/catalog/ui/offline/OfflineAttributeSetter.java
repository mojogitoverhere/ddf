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

import ddf.catalog.CatalogFramework;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Offline;
import ddf.catalog.operation.ProcessingDetails;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.security.SubjectUtils;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OfflineAttributeSetter {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineAttributeSetter.class);

  private static final Set<String> ATTRIBUTES_TO_REMOVE =
      new HashSet<>(
          Arrays.asList(
              Core.RESOURCE_URI,
              Core.RESOURCE_DOWNLOAD_URL,
              Core.DERIVED_RESOURCE_URI,
              Core.DERIVED_RESOURCE_DOWNLOAD_URL));

  private final CatalogFramework catalogFramework;

  public OfflineAttributeSetter(CatalogFramework catalogFramework) {
    this.catalogFramework = catalogFramework;
  }

  public void process(Metacard metacard, String comment, String outputPath)
      throws SourceUnavailableException, IngestException {
    if (!isRevision(metacard)) {
      setOfflineAttributes(metacard, comment, outputPath);
      update(metacard);
    }
  }

  private void update(Metacard metacard) throws SourceUnavailableException, IngestException {
    UpdateResponse updateResponse =
        catalogFramework.update(new UpdateRequestImpl(metacard.getId(), metacard));
    if (CollectionUtils.isNotEmpty(updateResponse.getProcessingErrors())) {
      LOGGER.debug(
          "Failed to update the catalog: metacardId=[{}] details=[{}]",
          metacard.getId(),
          stringifyProcessingErrors(updateResponse.getProcessingErrors()));
    }
  }

  private void setOfflineAttributes(Metacard metacard, String comment, String path) {
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_COMMENT, comment));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_LOCATION_PATH, path));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINED_BY, getWhoAmI()));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_DATE, new Date()));
    removeAttributes(metacard);
  }

  private void removeAttributes(Metacard metacard) {
    for (String attribute : ATTRIBUTES_TO_REMOVE) {
      metacard.setAttribute(new AttributeImpl(attribute, (Serializable) null));
    }
  }

  private String getWhoAmI() {
    Subject subject = SecurityUtils.getSubject();

    String whoami = SubjectUtils.getEmailAddress(subject);

    if (StringUtils.isEmpty(whoami)) {
      whoami = SubjectUtils.getName(subject);
    }

    return whoami;
  }

  private String stringifyProcessingErrors(Set<ProcessingDetails> details) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    for (ProcessingDetails detail : details) {
      pw.append(detail.getSourceId());
      pw.append(":");
      if (detail.hasException()) {
        detail.getException().printStackTrace(pw);
      }
      pw.append(System.lineSeparator());
    }
    return pw.toString();
  }

  private static boolean isRevision(Metacard metacard) {
    Set<String> tags = metacard.getTags();
    return CollectionUtils.isNotEmpty(tags) && tags.contains(MetacardVersion.VERSION_TAG);
  }
}
