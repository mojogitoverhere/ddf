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

import org.geotools.filter.visitor.DefaultExpressionVisitor;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;

class PropertyNameAndLiteralExtractor extends DefaultExpressionVisitor {

  private String propertyName;

  private String literal;

  String getPropertyName() {
    return propertyName;
  }

  String getLiteral() {
    return literal;
  }

  @Override
  public Object visit(Literal literal, Object data) {
    this.literal = literal.getValue().toString();
    return data;
  }

  @Override
  public Object visit(PropertyName propertyName, Object data) {
    this.propertyName = propertyName.getPropertyName();
    return data;
  }
}
