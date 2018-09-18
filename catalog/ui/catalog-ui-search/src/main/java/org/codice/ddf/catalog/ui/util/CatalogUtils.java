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
package org.codice.ddf.catalog.ui.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import ddf.catalog.CatalogFramework;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.data.impl.ContentItemImpl;
import ddf.catalog.content.operation.impl.CreateStorageRequestImpl;
import ddf.catalog.content.operation.impl.UpdateStorageRequestImpl;
import ddf.catalog.core.versioning.DeletedMetacard;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.core.versioning.impl.MetacardVersionImpl;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.ResourceRequestById;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.security.Subject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.subject.ExecutionException;
import org.codice.ddf.security.common.Security;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatalogUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogUtils.class);

  private static final Set<MetacardVersion.Action> CONTENT_ACTIONS =
      ImmutableSet.of(
          MetacardVersion.Action.VERSIONED_CONTENT, MetacardVersion.Action.DELETED_CONTENT);

  private static final Set<MetacardVersion.Action> DELETE_ACTIONS =
      ImmutableSet.of(MetacardVersion.Action.DELETED, MetacardVersion.Action.DELETED_CONTENT);

  private final EndpointUtil util;

  private final FilterBuilder filterBuilder;

  private final CatalogFramework catalogFramework;

  public CatalogUtils(
      EndpointUtil util, FilterBuilder filterBuilder, CatalogFramework catalogFramework) {
    this.util = util;
    this.filterBuilder = filterBuilder;
    this.catalogFramework = catalogFramework;
  }

  public Metacard revert(String metacardId, String revertId)
      throws UnsupportedQueryException, SourceUnavailableException, FederationException,
          IngestException, IOException, ResourceNotFoundException, ResourceNotSupportedException {
    Metacard versionMetacard = util.getMetacard(revertId);

    List<Result> queryResponse = getMetacardHistory(metacardId);
    if (queryResponse == null || queryResponse.isEmpty()) {
      throw new NotFoundException("Could not find metacard with id: " + metacardId);
    }

    Optional<Metacard> contentVersion =
        queryResponse
            .stream()
            .map(Result::getMetacard)
            .filter(
                mc ->
                    getVersionedOnDate(mc).isAfter(getVersionedOnDate(versionMetacard))
                        || getVersionedOnDate(mc).equals(getVersionedOnDate(versionMetacard)))
            .filter(mc -> CONTENT_ACTIONS.contains(MetacardVersion.Action.ofMetacard(mc)))
            .filter(mc -> mc.getResourceURI() != null)
            .filter(mc -> ContentItem.CONTENT_SCHEME.equals(mc.getResourceURI().getScheme()))
            .min(
                Comparator.comparing(
                    mc ->
                        util.parseToDate(
                            mc.getAttribute(MetacardVersion.VERSIONED_ON).getValue())));

    if (!contentVersion.isPresent()) {
      /* no content versions, just restore metacard */
      revertMetacard(versionMetacard, metacardId, false);
    } else {
      revertContentAndMetacard(contentVersion.get(), versionMetacard, metacardId);
    }

    return MetacardVersionImpl.toMetacard(versionMetacard);
  }

  public List<Result> getMetacardHistory(String id)
      throws UnsupportedQueryException, SourceUnavailableException, FederationException {
    Filter historyFilter =
        filterBuilder.attribute(Metacard.TAGS).is().equalTo().text(MetacardVersion.VERSION_TAG);
    Filter idFilter =
        filterBuilder.attribute(MetacardVersion.VERSION_OF_ID).is().equalTo().text(id);

    Filter filter = filterBuilder.allOf(historyFilter, idFilter);
    QueryResponse response =
        catalogFramework.query(
            new QueryRequestImpl(
                new QueryImpl(
                    filter, 1, -1, SortBy.NATURAL_ORDER, false, TimeUnit.SECONDS.toMillis(10)),
                false));
    return response.getResults();
  }

  private Instant getVersionedOnDate(Metacard mc) {
    return util.parseToDate(mc.getAttribute(MetacardVersion.VERSIONED_ON).getValue());
  }

  private void revertMetacard(Metacard versionMetacard, String id, boolean alreadyCreated)
      throws SourceUnavailableException, IngestException {
    LOGGER.trace("Reverting metacard [{}] to version [{}]", id, versionMetacard.getId());
    Metacard revertMetacard = MetacardVersionImpl.toMetacard(versionMetacard);
    MetacardVersion.Action action =
        MetacardVersion.Action.fromKey(
            (String) versionMetacard.getAttribute(MetacardVersion.ACTION).getValue());

    if (DELETE_ACTIONS.contains(action)) {
      attemptDeleteDeletedMetacard(id);
      if (!alreadyCreated) {
        catalogFramework.create(new CreateRequestImpl(revertMetacard));
      }
    } else {
      tryUpdate(
          4,
          () -> {
            catalogFramework.update(new UpdateRequestImpl(id, revertMetacard));
            return true;
          });
    }
  }

  private void revertContentAndMetacard(Metacard latestContent, Metacard versionMetacard, String id)
      throws SourceUnavailableException, IngestException, ResourceNotFoundException, IOException,
          ResourceNotSupportedException {
    LOGGER.trace(
        "Reverting content and metacard for metacard [{}]. \nLatest content: [{}] \nVersion metacard: [{}]",
        id,
        latestContent.getId(),
        versionMetacard.getId());
    Map<String, Serializable> properties = new HashMap<>();
    properties.put("no-default-tags", true);
    ResourceResponse latestResource =
        catalogFramework.getLocalResource(
            new ResourceRequestById(latestContent.getId(), properties));

    ContentItemImpl contentItem =
        new ContentItemImpl(
            id,
            new ByteSourceWrapper(() -> latestResource.getResource().getInputStream()),
            latestResource.getResource().getMimeTypeValue(),
            latestResource.getResource().getName(),
            latestResource.getResource().getSize(),
            MetacardVersionImpl.toMetacard(versionMetacard));

    // Try to delete the "deleted metacard" marker first.
    boolean alreadyCreated = false;
    MetacardVersion.Action action =
        MetacardVersion.Action.fromKey(
            (String) versionMetacard.getAttribute(MetacardVersion.ACTION).getValue());
    if (DELETE_ACTIONS.contains(action)) {
      alreadyCreated = true;
      catalogFramework.create(
          new CreateStorageRequestImpl(
              Collections.singletonList(contentItem), id, new HashMap<>()));
    } else {
      // Currently we can't guarantee the metacard will exist yet because of the 1 second
      // soft commit in solr. this busy wait loop should be fixed when alternate solution
      // is found.
      tryUpdate(
          4,
          () -> {
            catalogFramework.update(
                new UpdateStorageRequestImpl(
                    Collections.singletonList(contentItem), id, new HashMap<>()));
            return true;
          });
    }
    LOGGER.trace("Successfully reverted metacard content for [{}]", id);
    revertMetacard(versionMetacard, id, alreadyCreated);
  }

  private void attemptDeleteDeletedMetacard(String id) {
    LOGGER.trace("Attemping to delete metacard [{}]", id);
    Filter tags =
        filterBuilder.attribute(Metacard.TAGS).is().like().text(DeletedMetacard.DELETED_TAG);
    Filter deletion = filterBuilder.attribute(DeletedMetacard.DELETION_OF_ID).is().like().text(id);
    Filter filter = filterBuilder.allOf(tags, deletion);

    QueryResponse response = null;
    try {
      response = catalogFramework.query(new QueryRequestImpl(new QueryImpl(filter), false));
    } catch (UnsupportedQueryException | SourceUnavailableException | FederationException e) {
      LOGGER.debug("Could not find the deleted metacard marker to delete", e);
    }

    if (response == null || response.getResults() == null || response.getResults().size() != 1) {
      LOGGER.debug("There should have been one deleted metacard marker");
      return;
    }
    final QueryResponse _response = response;
    try {
      executeAsSystem(
          () ->
              catalogFramework.delete(
                  new DeleteRequestImpl(_response.getResults().get(0).getMetacard().getId())));
    } catch (ExecutionException e) {
      LOGGER.debug("Could not delete the deleted metacard marker", e);
    }
    LOGGER.trace("Deleted delete marker metacard successfully");
  }

  /**
   * Caution should be used with this, as it elevates the permissions to the System user.
   *
   * @param func What to execute as the System
   * @param <T> Generic return type of func
   */
  private <T> void executeAsSystem(Callable<T> func) {
    Subject systemSubject = Security.runAsAdmin(() -> Security.getInstance().getSystemSubject());
    if (systemSubject == null) {
      throw new RuntimeException("Could not get systemSubject to version metacards.");
    }
    systemSubject.execute(func);
  }

  private void trySleep() {
    try {
      Thread.sleep(350L);
    } catch (InterruptedException e) {
      // Do nothing
    }
  }

  private void tryUpdate(int retries, Callable<Boolean> func) throws IngestException {
    if (retries <= 0) {
      throw new IngestException("Could not update metacard!");
    }
    LOGGER.trace("Trying to update metacard.");
    try {
      func.call();
      LOGGER.trace("Successfully updated metacard.");
    } catch (Exception e) {
      LOGGER.trace("Failed to update metacard");
      trySleep();
      tryUpdate(retries - 1, func);
    }
  }

  private static class ByteSourceWrapper extends ByteSource {
    Supplier<InputStream> supplier;

    ByteSourceWrapper(Supplier<InputStream> supplier) {
      this.supplier = supplier;
    }

    @Override
    public InputStream openStream() {
      return supplier.get();
    }
  }
}
