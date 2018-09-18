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

import static org.mockito.Matchers.argThat;

import ddf.catalog.operation.QueryRequest;
import org.mockito.ArgumentMatcher;

/** Matches a query request that is searching for a specific metacardId. */
class QueryRequestMatcher extends ArgumentMatcher<QueryRequest> {

  private String metacardId;

  private QueryRequestMatcher(String metacardId) {
    this.metacardId = metacardId;
  }

  @Override
  public boolean matches(Object obj) {
    if (!(obj instanceof QueryRequest)) {
      return false;
    }

    QueryRequest queryRequest = (QueryRequest) obj;

    MetacardIdExtractor metacardIdExtractor = new MetacardIdExtractor();

    queryRequest.getQuery().accept(metacardIdExtractor, null);

    return metacardIdExtractor.getMetacardId().map(this::isTargetMetacardId).orElse(false);
  }

  private boolean isTargetMetacardId(String id) {
    return id.equals(metacardId);
  }

  public static QueryRequest is(String metacardId) {
    return argThat(new QueryRequestMatcher(metacardId));
  }
}
