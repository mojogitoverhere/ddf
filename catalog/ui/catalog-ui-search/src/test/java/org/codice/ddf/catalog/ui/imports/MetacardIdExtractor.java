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

import ddf.catalog.data.Metacard;
import java.util.Optional;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.opengis.filter.PropertyIsEqualTo;

class MetacardIdExtractor extends DefaultFilterVisitor {

  private String metacardId;

  Optional<String> getMetacardId() {
    return Optional.ofNullable(metacardId);
  }

  @Override
  public Object visit(PropertyIsEqualTo propertyIsEqualTo, Object data) {

    PropertyNameAndLiteralExtractor propertyNameAndLiteralExtractor =
        new PropertyNameAndLiteralExtractor();

    propertyIsEqualTo.getExpression1().accept(propertyNameAndLiteralExtractor, data);
    propertyIsEqualTo.getExpression2().accept(propertyNameAndLiteralExtractor, data);

    if (Metacard.ID.equals(propertyNameAndLiteralExtractor.getPropertyName())) {
      metacardId = propertyNameAndLiteralExtractor.getLiteral();
    }

    return data;
  }
}
