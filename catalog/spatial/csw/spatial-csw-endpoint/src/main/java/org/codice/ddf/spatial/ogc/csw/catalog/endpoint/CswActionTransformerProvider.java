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
package org.codice.ddf.spatial.ogc.csw.catalog.endpoint;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.codice.ddf.spatial.ogc.csw.catalog.common.transformer.CswActionTransformer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Manages a reference list of {@link CswActionTransformer}'s by mapping them to the QName's they
 * apply to.
 */
public class CswActionTransformerProvider {
  private TransformerProvider<CswActionTransformer> transformerProvider =
      new TransformerProvider<>();

  public synchronized void bind(ServiceReference<CswActionTransformer> reference) {
    transformerProvider.bind(reference);
  }

  public synchronized void unbind(ServiceReference<CswActionTransformer> reference) {
    transformerProvider.unbind(reference);
  }

  public synchronized Optional<CswActionTransformer> getTransformer(@Nullable String typeName) {
    return transformerProvider.getTransformer(typeName);
  }

  public synchronized Optional<CswActionTransformer> getTransformer(QName qName) {
    return transformerProvider.getTransformer(qName);
  }

  public BundleContext getBundleContext() {
    return transformerProvider.getBundleContext();
  }
}
