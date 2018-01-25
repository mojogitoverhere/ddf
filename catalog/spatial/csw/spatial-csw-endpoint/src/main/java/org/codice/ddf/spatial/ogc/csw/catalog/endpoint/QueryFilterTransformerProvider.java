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

import ddf.catalog.transform.QueryFilterTransformer;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Manages a reference list of {@link QueryFilterTransformer}'s by mapping them to the {@link
 * QName}'s they apply to.
 */
public class QueryFilterTransformerProvider {

  private TransformerProvider<QueryFilterTransformer> transformerProvider =
      new TransformerProvider<>();

  public synchronized void bind(ServiceReference<QueryFilterTransformer> reference) {
    transformerProvider.bind(reference);
  }

  public synchronized void unbind(ServiceReference<QueryFilterTransformer> reference) {
    transformerProvider.unbind(reference);
  }

  public synchronized Optional<QueryFilterTransformer> getTransformer(@Nullable String typeName) {
    return transformerProvider.getTransformer(typeName);
  }

  public synchronized Optional<QueryFilterTransformer> getTransformer(QName qName) {
    return transformerProvider.getTransformer(qName);
  }

  public BundleContext getBundleContext() {
    return transformerProvider.getBundleContext();
  }
}
