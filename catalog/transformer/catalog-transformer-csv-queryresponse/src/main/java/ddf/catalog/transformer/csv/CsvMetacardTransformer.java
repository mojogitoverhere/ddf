/*
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.transformer.csv;

import static ddf.catalog.transformer.csv.common.CsvTransformer.createResponse;
import static ddf.catalog.transformer.csv.common.CsvTransformer.writeMetacardsToCsv;

import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.codice.ddf.catalog.ui.alias.AttributeAliases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvMetacardTransformer implements MetacardTransformer {

  private static final Logger LOGGER = LoggerFactory.getLogger(CsvMetacardTransformer.class);

  private final AttributeAliases aliases;

  public CsvMetacardTransformer(AttributeAliases aliases) {
    this.aliases = aliases;
  }

  @Override
  public BinaryContent transform(Metacard metacard, Map<String, Serializable> arguments)
      throws CatalogTransformerException {

    if (metacard == null) {
      LOGGER.debug("Attempted to transform null metacard");
      throw new CatalogTransformerException("Unable to transform null metacard");
    }

    String columnOrderString =
        arguments.get("columnOrder") != null ? (String) arguments.get("columnOrder") : "";
    List<String> columnOrder = Arrays.asList((columnOrderString).split(","));
    Set<AttributeDescriptor> descriptors = metacard.getMetacardType().getAttributeDescriptors();
    List<AttributeDescriptor> orderedDescriptors =
        columnOrder.isEmpty()
            ? new ArrayList<>(descriptors)
            : getFilteredDescriptors(metacard, columnOrder);

    Appendable appendable =
        writeMetacardsToCsv(
            Collections.singletonList(metacard), orderedDescriptors, aliases.getAliasMap());
    return createResponse(appendable);
  }

  private List<AttributeDescriptor> getFilteredDescriptors(
      Metacard metacard, List<String> orderedAttributes) {
    MetacardType metacardType = metacard.getMetacardType();
    return orderedAttributes
        .stream()
        .map(metacardType::getAttributeDescriptor)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
