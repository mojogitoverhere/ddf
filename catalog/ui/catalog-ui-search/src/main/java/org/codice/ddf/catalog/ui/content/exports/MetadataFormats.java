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

import ddf.catalog.transform.ExportableMetadataTransformer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataFormats {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataFormats.class);

  private Map<String, ExportableMetadataTransformer> transformersById = new HashMap<>();

  public Optional<ExportableMetadataTransformer> getTransformerById(String transformerId) {
    return Optional.ofNullable(transformersById.get(transformerId));
  }

  public List<String> getIds() {
    return transformersById
        .keySet()
        .stream()
        .sorted(String::compareToIgnoreCase)
        .collect(Collectors.toList());
  }

  public synchronized void bind(ServiceReference<ExportableMetadataTransformer> reference) {
    Optional<String> id = getIdFromReference(reference);
    Optional<ExportableMetadataTransformer> transformer = getTransformerFromReference(reference);
    if (!id.isPresent()) {
      LOGGER.trace("Could not bind service reference without an id");
      return;
    }
    if (!transformer.isPresent()) {
      LOGGER.trace("Could not bind service reference without a transformer");
      return;
    }

    transformersById.put(id.get(), transformer.get());
  }

  public synchronized void unbind(ServiceReference<ExportableMetadataTransformer> reference) {
    if (reference == null) {
      return;
    }

    try {
      Optional<String> id = getIdFromReference(reference);
      if (id.isPresent()) {
        transformersById.remove(id.get());
      }
    } finally {
      getBundleContext().ungetService(reference);
    }
  }

  private Optional<String> getIdFromReference(
      ServiceReference<ExportableMetadataTransformer> reference) {
    if (reference == null) {
      return Optional.empty();
    }

    Object idObj = reference.getProperty("id");
    return idObj instanceof String ? Optional.of((String) idObj) : Optional.empty();
  }

  private Optional<ExportableMetadataTransformer> getTransformerFromReference(
      ServiceReference<ExportableMetadataTransformer> reference) {
    if (reference == null) {
      return Optional.empty();
    }

    ExportableMetadataTransformer transformer = getBundleContext().getService(reference);
    return Optional.ofNullable(transformer);
  }

  BundleContext getBundleContext() {
    return FrameworkUtil.getBundle(this.getClass()).getBundleContext();
  }
}
