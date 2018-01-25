/*
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */

package org.codice.ddf.spatial.ogc.csw.catalog.endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/** Manages a reference list of {@link T}'s by mapping them to the {@link QName}'s they apply to. */
public class TransformerProvider<T> {
  //  private static final Logger LOGGER = LoggerFactory.getLogger(TransformerProvider.class);

  private Map<QName, T> transformerMap = new ConcurrentHashMap<>();

  private final Map<String, QName> typeNameQNameMap = new HashMap<>();

  public synchronized void bind(ServiceReference<T> reference) {
    if (reference == null) {
      return;
    }

    List<QName> namespaces = getNamespaces(reference);
    T transformer = getTransformer(reference);

    for (QName namespace : namespaces) {
      transformerMap.put(namespace, transformer);
      List<String> typeNames = getTypeNames(reference);

      for (String typeName : typeNames) {
        typeNameQNameMap.put(typeName, namespace);
      }
    }
  }

  public synchronized void unbind(ServiceReference<T> reference) {
    if (reference == null) {
      return;
    }
    try {
      List<QName> namespaces = getNamespaces(reference);
      for (QName namespace : namespaces) {
        transformerMap.remove(namespace);
        List<String> typeNames = getTypeNames(reference);
        for (String typeName : typeNames) {
          typeNameQNameMap.remove(typeName, namespace);
        }
      }
    } finally {
      getBundleContext().ungetService(reference);
    }
  }

  public synchronized Optional<T> getTransformer(@Nullable String typeName) {
    if (StringUtils.isEmpty(typeName)) {
      return Optional.empty();
    }

    QName qName = typeNameQNameMap.get(typeName);
    if (qName == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(transformerMap.get(qName));
  }

  public synchronized Optional<T> getTransformer(QName qName) {
    if (qName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(transformerMap.get(qName));
  }

  private List<String> getTypeNames(ServiceReference<T> reference) {
    Object typeNameObject =
        reference.getProperty(CswEndpoint.QUERY_FILTER_TRANSFORMER_TYPE_NAMES_FIELD);
    if (typeNameObject instanceof List) {
      return (List<String>) typeNameObject;
    }
    return Collections.emptyList();
  }

  private List<QName> getNamespaces(ServiceReference<T> reference) {
    Object id = reference.getProperty("id");
    List<QName> result = new ArrayList<>();
    if (id instanceof List) {
      List<String> namespaces = (List<String>) id;
      for (String namespace : namespaces) {
        result.add(QName.valueOf(namespace));
      }
    } else if (id instanceof String) {
      result.add(QName.valueOf((String) id));
    } else {
      //      LOGGER.debug("T reference has a bad ID property. Must be of type String or
      // List<String>");
      throw new IllegalArgumentException("id must be of type String or a list of Strings");
    }

    return result;
  }

  private T getTransformer(ServiceReference<T> reference) {
    BundleContext bundleContext = getBundleContext();

    T transformer = bundleContext.getService(reference);

    if (transformer == null) {
      //      LOGGER.debug("Failed to find a T with service reference {}", reference);
      throw new IllegalStateException(
          "Attempted to retrieve an unregistered service: " + reference);
    }

    return transformer;
  }

  public BundleContext getBundleContext() {
    return FrameworkUtil.getBundle(this.getClass()).getBundleContext();
  }
}
