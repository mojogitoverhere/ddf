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
package org.codice.ddf.catalog.ui.offline.command;

import ddf.catalog.CatalogFramework;
import ddf.catalog.core.versioning.DeletedMetacard;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.codice.ddf.catalog.ui.config.ConfigurationApplication;
import org.codice.ddf.catalog.ui.metacard.MetacardApplication;
import org.codice.ddf.catalog.ui.util.CatalogUtils;
import org.codice.ddf.commands.util.QueryResulterable;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Experimental. */
@Service
@Command(
  scope = "ui",
  name = "offline",
  description = "Offlines the given metacard ids given (or by query)"
)
public class OfflineCommand implements Action {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineCommand.class);

  @Option(
    name = "--cql",
    required = false,
    aliases = {"-cqlFilter"},
    multiValued = false,
    description =
        "Option to filter by metacards that match a CQL Filter expression. It is recommended to "
            + "use the search command (catalog:search) first to see which metacards will be filtered.\n\n"
  )
  String cqlFilter = null;

  @Option(
    name = "--comment",
    required = false,
    aliases = {"-c"},
    multiValued = false,
    description = "A comment to set on each offlined metacard."
  )
  String offlineCommentArgument = "No Comment";

  @Option(
    name = "--deleted",
    required = false,
    aliases = {"-d"},
    multiValued = false,
    description = "Offline all deleted items"
  )
  boolean deleted;

  @Option(
    name = "--expired",
    required = false,
    aliases = {"-e"},
    multiValued = false,
    description = "Offline all expired items"
  )
  boolean expired;

  @Option(
    name = "--ids",
    required = false,
    aliases = {"-i"},
    multiValued = false,
    description = "Specify a list of ids to offline. May not be used with '--cql'."
  )
  String idList;

  @Reference CatalogFramework catalogFramework;

  @Reference FilterBuilder filterBuilder;

  public OfflineCommand() {}

  @Override
  public Object execute() throws Exception {
    String offlinePath = getOfflineRootPath();

    String offlineComment = offlineCommentArgument;
    List<String> ids;
    if (StringUtils.isNotBlank(idList)) {
      if (StringUtils.isNotBlank(cqlFilter)) {
        System.out.println(
            "--ids and --cql are mutually exclusive. Please use only one or the other.");
        return null;
      }
      ids = Arrays.asList(idList.split("\\s?,\\s?"));
    } else {

      List<Filter> filters = new ArrayList<>(4);

      if (StringUtils.isNotBlank(cqlFilter)) {
        filters.add(getCqlFilter(cqlFilter));
      }

      if (deleted) {
        filters.add(getDeletedFilter());
      }

      if (expired) {
        filters.add(getExpiredFilter());
      }

      if (getPermanenceEnabled()) {
        filters.add(getPermanenceFilter());
      }

      ids =
          CatalogUtils.executeAsSystem(
              () ->
                  queryAsLocalOnly(filterBuilder.allOf(filters))
                      .stream()
                      .map(Result::getMetacard)
                      .map(Metacard::getId)
                      .collect(Collectors.toList()));
    }
    CatalogUtils.executeAsSystem(
        () -> MetacardApplication.getInstance().offlineMetacards(ids, offlineComment));

    System.out.println("Offline Completed.");
    return null;
  }

  private Filter getCqlFilter(String cql) {
    Filter filter = null;
    try {
      filter = ECQL.toFilter(cql);
    } catch (CQLException e) {
      System.out.println("Could not parse the cql filter. (Error: " + e.getMessage() + ")");
      LOGGER.error("Could not parse cql filter", e);
      throw new RuntimeException(e);
    }
    return filter;
  }

  private String getOfflineRootPath() {
    String offlinePath = System.getProperty(ConfigurationApplication.OFFLINE_ROOT_PATH_PROPERTY);
    if (StringUtils.isNotBlank(offlinePath)) {
      return offlinePath;
    }

    return System.getProperty("ddf.home");
  }

  private Filter getPermanenceFilter() {
    return filterBuilder.attribute(getPermanenceAttributeName()).is().equalTo().bool(false);
  }

  private String getPermanenceAttributeName() {
    String permanentAttributeName =
        System.getProperty("org.codice.ddf.catalog.ui.config.permanenceattribute");
    if (StringUtils.isNotBlank(permanentAttributeName)) {
      return permanentAttributeName;
    }

    return "permanent";
  }

  private boolean getPermanenceEnabled() {
    String permanenceEnabled =
        System.getProperty("org.codice.ddf.catalog.ui.config.permanenceenabled");
    if (StringUtils.isNotBlank(permanenceEnabled)) {
      // Explicitly look for false, so in all other cases we have the correct default of true
      return !permanenceEnabled.equalsIgnoreCase("false");
    }
    return true;
  }

  private Filter getDeletedFilter() {
    return filterBuilder
        .attribute(CoreAttributes.METACARD_TAGS)
        .is()
        .like()
        .text(DeletedMetacard.DELETED_TAG);
  }

  private Filter getExpiredFilter() {
    return filterBuilder
        .attribute(CoreAttributes.EXPIRATION)
        .is()
        .before()
        .date(Date.from(Instant.now()));
  }

  private QueryResulterable queryAsLocalOnly(Filter filter) {
    return new QueryResulterable(catalogFramework, i -> createLocalOnlyQuery(filter, i));
  }

  private QueryRequest createLocalOnlyQuery(Filter filter, int index) {
    return new QueryRequestImpl(
        new QueryImpl(filter, index, 64, SortBy.NATURAL_ORDER, false, TimeUnit.MINUTES.toMillis(1)),
        false,
        Collections.singleton(catalogFramework.getId()),
        new HashMap<>());
  }
}
