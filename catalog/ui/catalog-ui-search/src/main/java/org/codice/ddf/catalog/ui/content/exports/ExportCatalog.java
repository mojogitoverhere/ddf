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

import ddf.catalog.CatalogFramework;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Metacard;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.codice.ddf.catalog.ui.query.cql.CqlRequest;
import org.codice.ddf.commands.util.QueryResulterable;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;

public class ExportCatalog {

  private static final int PAGE_SIZE = 64;

  private final CatalogFramework catalogFramework;

  private final FilterBuilder filterBuilder;

  public ExportCatalog(CatalogFramework catalogFramework, FilterBuilder filterBuilder) {
    this.catalogFramework = catalogFramework;
    this.filterBuilder = filterBuilder;
  }

  public QueryResulterable queryAsLocalOnly(String cql) {
    CqlRequest cqlRequest = new CqlRequest();
    cqlRequest.setCql(cql);
    QueryRequest localQuery =
        cqlRequest.createQueryRequest(catalogFramework.getId(), filterBuilder);

    return new QueryResulterable(
        catalogFramework, i -> createLocalOnlyQuery(localQuery.getQuery(), i));
  }

  public QueryRequest getLocalResultCountQueryRequest(String cql) {
    CqlRequest cqlRequest = new CqlRequest();
    cqlRequest.setCount(1);
    cqlRequest.setCql(cql);
    return cqlRequest.createQueryRequest(catalogFramework.getId(), filterBuilder);
  }

  public QueryResulterable getLocalHistory(String metacardId) {
    return new QueryResulterable(
        catalogFramework, i -> createLocalOnlyQuery(createHistoryFilter(metacardId), i));
  }

  private Filter createHistoryFilter(String metacardId) {
    return filterBuilder.allOf(
        Arrays.asList(
            filterBuilder.anyOf(
                filterBuilder.attribute(Metacard.ID).equalTo().text(metacardId),
                filterBuilder.attribute(MetacardVersion.VERSION_OF_ID).equalTo().text(metacardId)),
            filterBuilder.attribute(Metacard.TAGS).like().text("*")));
  }

  private QueryRequest createLocalOnlyQuery(Filter filter, int index) {
    return new QueryRequestImpl(
        new QueryImpl(
            filter, index, PAGE_SIZE, SortBy.NATURAL_ORDER, true, TimeUnit.MINUTES.toMillis(1)),
        false,
        Collections.singleton(catalogFramework.getId()),
        new HashMap<>());
  }
}
