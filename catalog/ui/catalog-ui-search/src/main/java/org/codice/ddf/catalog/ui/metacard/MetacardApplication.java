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
package org.codice.ddf.catalog.ui.metacard;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static spark.Spark.after;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.patch;
import static spark.Spark.post;
import static spark.Spark.put;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.oath.cyclops.util.ExceptionSoftener;
import cyclops.async.SimpleReact;
import cyclops.collections.mutable.ListX;
import ddf.catalog.CatalogFramework;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.data.impl.ContentItemImpl;
import ddf.catalog.content.operation.impl.CreateStorageRequestImpl;
import ddf.catalog.content.operation.impl.UpdateStorageRequestImpl;
import ddf.catalog.core.versioning.DeletedMetacard;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.core.versioning.MetacardVersion.Action;
import ddf.catalog.core.versioning.impl.MetacardVersionImpl;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeRegistry;
import ddf.catalog.data.AttributeType;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.ResultImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.SourceInfoResponse;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.QueryResponseImpl;
import ddf.catalog.operation.impl.ResourceRequestById;
import ddf.catalog.operation.impl.SourceInfoRequestEnterprise;
import ddf.catalog.operation.impl.SourceResponseImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.CatalogStore;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceDescriptor;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.QueryResponseTransformer;
import ddf.security.Subject;
import ddf.security.SubjectUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import javax.ws.rs.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.ExecutionException;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.catalog.ui.config.ConfigurationApplication;
import org.codice.ddf.catalog.ui.metacard.associations.Associated;
import org.codice.ddf.catalog.ui.metacard.edit.AttributeChange;
import org.codice.ddf.catalog.ui.metacard.edit.MetacardChanges;
import org.codice.ddf.catalog.ui.metacard.enumerations.ExperimentalEnumerationExtractor;
import org.codice.ddf.catalog.ui.metacard.history.HistoryResponse;
import org.codice.ddf.catalog.ui.metacard.transform.CsvTransform;
import org.codice.ddf.catalog.ui.metacard.validation.Validator;
import org.codice.ddf.catalog.ui.metacard.workspace.WorkspaceAttributes;
import org.codice.ddf.catalog.ui.metacard.workspace.WorkspaceTransformer;
import org.codice.ddf.catalog.ui.query.monitor.api.SubscriptionsPersistentStore;
import org.codice.ddf.catalog.ui.util.EndpointUtil;
import org.codice.ddf.security.common.Security;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.servlet.SparkApplication;

public class MetacardApplication implements SparkApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetacardApplication.class);

  private static final String UPDATE_ERROR_MESSAGE = "Item is either restricted or not found.";

  private static final Set<Action> CONTENT_ACTIONS =
      ImmutableSet.of(Action.VERSIONED_CONTENT, Action.DELETED_CONTENT);

  private static final Set<Action> DELETE_ACTIONS =
      ImmutableSet.of(Action.DELETED, Action.DELETED_CONTENT);

  private static final Security SECURITY = Security.getInstance();

  private static final QueryResponseImpl EMPTY_QUERY_RESPONSE =
      new QueryResponseImpl(new QueryRequestImpl(null), Collections.emptyList(), 0);

  private static final Executor INTRIGUE_EXECUTOR =
      new ForkJoinPool(
          Integer.parseInt(
              System.getProperty("org.codice.ddf.catalog.intrigue.threadpoolsize", "128")));

  private static final int QUERY_PARTIONING_SIZE =
      Integer.parseInt(
          System.getProperty("org.codice.ddf.catalog.intrigue.querypartitioningsize", "64"));

  private static final boolean DELETECHECK_SHORTCIRCUIT =
      Boolean.parseBoolean(
          System.getProperty("org.codice.ddf.catalog.intrigue.deletecheck.shortcircuit", "true"));

  private final ObjectMapper mapper =
      JsonFactory.create(
          new JsonParserFactory().usePropertyOnly(),
          new JsonSerializerFactory()
              .includeEmpty()
              .includeNulls()
              .includeDefaultValues()
              .setJsonFormatForDates(false));

  private final CatalogFramework catalogFramework;

  private final FilterBuilder filterBuilder;

  private final EndpointUtil util;

  private final Validator validator;

  private final WorkspaceTransformer transformer;

  private final ExperimentalEnumerationExtractor enumExtractor;

  private final SubscriptionsPersistentStore subscriptions;

  private final List<MetacardType> types;

  private final List<CatalogStore> catalogStores;

  private final Associated associated;

  private final QueryResponseTransformer csvQueryResponseTransformer;

  private final AttributeRegistry attributeRegistry;

  private final ConfigurationApplication configuration;

  public MetacardApplication(
      CatalogFramework catalogFramework,
      FilterBuilder filterBuilder,
      EndpointUtil endpointUtil,
      Validator validator,
      WorkspaceTransformer transformer,
      ExperimentalEnumerationExtractor enumExtractor,
      SubscriptionsPersistentStore subscriptions,
      List<MetacardType> types,
      Associated associated,
      List<CatalogStore> catalogStores,
      QueryResponseTransformer csvQueryResponseTransformer,
      AttributeRegistry attributeRegistry,
      ConfigurationApplication configuration) {
    this.catalogFramework = catalogFramework;
    this.filterBuilder = filterBuilder;
    this.util = endpointUtil;
    this.validator = validator;
    this.transformer = transformer;
    this.enumExtractor = enumExtractor;
    this.subscriptions = subscriptions;
    this.types = types;
    this.associated = associated;
    this.catalogStores = catalogStores;
    this.csvQueryResponseTransformer = csvQueryResponseTransformer;
    this.attributeRegistry = attributeRegistry;
    this.configuration = configuration;
  }

  private String getSubjectEmail() {
    return SubjectUtils.getEmailAddress(SecurityUtils.getSubject());
  }

  @Override
  public void init() {

    get("/metacardtype", (req, res) -> util.getJson(util.getMetacardTypeMap()));

    get(
        "/metacard/:id",
        (req, res) -> {
          String id = req.params(":id");
          return util.metacardToJson(id);
        });

    get(
        "/metacard/:id/attribute/validation",
        (req, res) -> {
          String id = req.params(":id");
          return util.getJson(validator.getValidation(util.getMetacard(id)));
        });

    get(
        "/metacard/:id/validation",
        (req, res) -> {
          String id = req.params(":id");
          return util.getJson(validator.getFullValidation(util.getMetacard(id)));
        });

    post(
        "/metacards",
        APPLICATION_JSON,
        (req, res) -> {
          List<String> ids = JsonFactory.create().parser().parseList(String.class, req.body());
          List<Metacard> metacards =
              util.getMetacards(ids, "*")
                  .entrySet()
                  .stream()
                  .map(Map.Entry::getValue)
                  .map(Result::getMetacard)
                  .collect(Collectors.toList());

          return util.metacardsToJson(metacards);
        });

    delete(
        "/metacards",
        APPLICATION_JSON,
        (req, res) -> {
          List<String> ids = JsonFactory.create().parser().parseList(String.class, req.body());
          DeleteResponse deleteResponse =
              catalogFramework.delete(
                  new DeleteRequestImpl(new ArrayList<>(ids), Metacard.ID, null));
          if (deleteResponse.getProcessingErrors() != null
              && !deleteResponse.getProcessingErrors().isEmpty()) {
            res.status(500);
            return ImmutableMap.of("message", "Unable to delete metacards.");
          }

          return ImmutableMap.of("message", "Successfully deleted metacards.");
        },
        util::getJson);

    patch(
        "/metacards",
        APPLICATION_JSON,
        (req, res) -> {
          List<MetacardChanges> metacardChanges =
              JsonFactory.createUseJSONDates()
                  .parser()
                  .parseList(MetacardChanges.class, req.body());

          UpdateResponse updateResponse = patchMetacards(metacardChanges);
          if (updateResponse.getProcessingErrors() != null
              && !updateResponse.getProcessingErrors().isEmpty()) {
            res.status(500);
            return updateResponse.getProcessingErrors();
          }

          return req.body();
        });

    put(
        "/validate/attribute/:attribute",
        TEXT_PLAIN,
        (req, res) -> {
          String attribute = req.params(":attribute");
          String value = req.body();
          return util.getJson(validator.validateAttribute(attribute, value));
        });

    get(
        "/history/:id",
        (req, res) -> {
          String id = req.params(":id");
          List<Result> queryResponse = getMetacardHistory(id);
          if (queryResponse.isEmpty()) {
            res.status(204);
            return "[]";
          }
          List<HistoryResponse> response =
              queryResponse
                  .stream()
                  .map(Result::getMetacard)
                  .map(
                      mc ->
                          new HistoryResponse(
                              mc.getId(),
                              (String) mc.getAttribute(MetacardVersion.EDITED_BY).getValue(),
                              (Date) mc.getAttribute(MetacardVersion.VERSIONED_ON).getValue()))
                  .sorted(Comparator.comparing(HistoryResponse::getVersioned))
                  .collect(Collectors.toList());
          return util.getJson(response);
        });

    get(
        "/history/revert/:id/:revertid",
        (req, res) -> {
          String id = req.params(":id");
          String revertId = req.params(":revertid");

          Metacard versionMetacard = util.getMetacard(revertId);

          List<Result> queryResponse = getMetacardHistory(id);
          if (queryResponse == null || queryResponse.isEmpty()) {
            throw new NotFoundException("Could not find metacard with id: " + id);
          }

          Optional<Metacard> contentVersion =
              queryResponse
                  .stream()
                  .map(Result::getMetacard)
                  .filter(
                      mc ->
                          getVersionedOnDate(mc).isAfter(getVersionedOnDate(versionMetacard))
                              || getVersionedOnDate(mc).equals(getVersionedOnDate(versionMetacard)))
                  .filter(mc -> CONTENT_ACTIONS.contains(Action.ofMetacard(mc)))
                  .filter(mc -> mc.getResourceURI() != null)
                  .filter(mc -> ContentItem.CONTENT_SCHEME.equals(mc.getResourceURI().getScheme()))
                  .sorted(
                      Comparator.comparing(
                          (Metacard mc) ->
                              util.parseToDate(
                                  mc.getAttribute(MetacardVersion.VERSIONED_ON).getValue())))
                  .findFirst();

          if (!contentVersion.isPresent()) {
            /* no content versions, just restore metacard */
            revertMetacard(versionMetacard, id, false);
          } else {
            revertContentandMetacard(contentVersion.get(), versionMetacard, id);
          }
          return util.metacardToJson(MetacardVersionImpl.toMetacard(versionMetacard, types));
        });

    get(
        "/associations/:id",
        (req, res) -> {
          String id = req.params(":id");
          Collection<Associated.Edge> associations =
              associated.getAssociations(getWritableSources(), id);
          return util.getJson(associations);
        });

    put(
        "/associations/:id",
        (req, res) -> {
          String id = req.params(":id");
          List<Associated.Edge> edges =
              JsonFactory.create().parser().parseList(Associated.Edge.class, req.body());
          associated.putAssociations(getWritableSources(), id, edges);
          return req.body();
        });

    post(
        "/metacards/deletecheck",
        (req, res) -> {
          String shortCircuitStr = req.queryParams("shortcircuit");
          boolean shortCircuit =
              isNotEmpty(shortCircuitStr)
                  ? Boolean.parseBoolean(shortCircuitStr)
                  : DELETECHECK_SHORTCIRCUIT;

          Set<String> idsToCheck =
              new HashSet<>(JsonFactory.create().parser().parseList(String.class, req.body()));

          Set<SourceDescriptor> sourceInfos = getSources().getSourceInfo();

          // Produces <number of sources> * <number of partitions> queries
          List<QueryRequestImpl> queries =
              Lists.partition(new ArrayList<>(idsToCheck), QUERY_PARTIONING_SIZE)
                  .stream()
                  .map(
                      partitionedIdsToCheck ->
                          filterBuilder.anyOf(
                              partitionedIdsToCheck
                                  .stream()
                                  .map(this::getParentAndChildrenFilter)
                                  .collect(Collectors.toList())))
                  .flatMap(
                      targetSubset ->
                          sourceInfos.stream().map(sd -> createQueryForSource(targetSubset, sd)))
                  .collect(Collectors.toList());

          ListX<String> brokenAssociations =
              new SimpleReact(INTRIGUE_EXECUTOR, true)
                  .from(queries)
                  .then(ExceptionSoftener.softenFunction(catalogFramework::query))
                  .sync()
                  .onFail(e -> EMPTY_QUERY_RESPONSE)
                  .thenSync(
                      qr ->
                          qr.getResults()
                              .stream()
                              .map(Result::getMetacard)
                              .flatMap(m -> extractPotentialBrokenAssociations(idsToCheck, m))
                              .filter(id -> !idsToCheck.contains(id))
                              .collect(ListX.listXCollector()))
                  .block(
                      Collectors.reducing(ListX.empty(), ListX::plusAll),
                      status ->
                          shortCircuit
                              && status
                                  .getResultsSoFar()
                                  .stream()
                                  .anyMatch(list -> !list.isEmpty()));

          return ImmutableMap.of("broken-links", brokenAssociations);
        },
        util::getJson);

    post(
        "/subscribe/:id",
        (req, res) -> {
          String email = getSubjectEmail();
          if (isEmpty(email)) {
            throw new NotFoundException("Login to subscribe to workspace.");
          }
          String id = req.params(":id");
          subscriptions.addEmail(id, email);
          return ImmutableMap.of(
              "message", String.format("Successfully subscribed to id = %s.", id));
        },
        util::getJson);

    post(
        "/unsubscribe/:id",
        (req, res) -> {
          String email = getSubjectEmail();
          if (isEmpty(email)) {
            throw new NotFoundException("Login to un-subscribe from workspace.");
          }
          String id = req.params(":id");
          subscriptions.removeEmail(id, email);
          return ImmutableMap.of(
              "message", String.format("Successfully un-subscribed to id = %s.", id));
        },
        util::getJson);

    get(
        "/workspaces/:id",
        (req, res) -> {
          String id = req.params(":id");
          String email = getSubjectEmail();
          Metacard metacard = util.getMetacard(id);

          // NOTE: the isEmpty is to guard against users with no email (such as guest).
          boolean isSubscribed =
              !isEmpty(email) && subscriptions.getEmails(metacard.getId()).contains(email);

          return ImmutableMap.builder()
              .putAll(transformer.transform(metacard))
              .put("subscribed", isSubscribed)
              .build();
        },
        util::getJson);

    get(
        "/workspaces",
        (req, res) -> {
          String email = getSubjectEmail();
          Map<String, Result> workspaceMetacards =
              util.getMetacardsByFilter(WorkspaceAttributes.WORKSPACE_TAG);

          // NOTE: the isEmpty is to guard against users with no email (such as guest).
          Set<String> ids =
              isEmpty(email) ? Collections.emptySet() : subscriptions.getSubscriptions(email);

          return workspaceMetacards
              .entrySet()
              .stream()
              .map(Map.Entry::getValue)
              .map(Result::getMetacard)
              .map(
                  metacard -> {
                    boolean isSubscribed = ids.contains(metacard.getId());
                    try {
                      return ImmutableMap.builder()
                          .putAll(transformer.transform(metacard))
                          .put("subscribed", isSubscribed)
                          .build();
                    } catch (RuntimeException e) {
                      LOGGER.debug(
                          "Could not transform metacard. WARNING: This indicates there is invalid data in the system. Metacard title: '{}', id:'{}'",
                          metacard.getTitle(),
                          metacard.getId(),
                          e);
                    }
                    return null;
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
        },
        util::getJson);

    post(
        "/workspaces",
        APPLICATION_JSON,
        (req, res) -> {
          Map<String, Object> incoming = JsonFactory.create().parser().parseMap(req.body());
          Metacard saved = saveMetacard(transformer.transform(incoming));
          Map<String, Object> response = transformer.transform(saved);

          res.status(201);
          return util.getJson(response);
        });

    put(
        "/workspaces/:id",
        APPLICATION_JSON,
        (req, res) -> {
          String id = req.params(":id");

          Map<String, Object> workspace = JsonFactory.create().parser().parseMap(req.body());

          Metacard metacard = transformer.transform(workspace);
          metacard.setAttribute(new AttributeImpl(Metacard.ID, id));

          Metacard updated = updateMetacard(id, metacard);
          return util.getJson(transformer.transform(updated));
        });

    delete(
        "/workspaces/:id",
        APPLICATION_JSON,
        (req, res) -> {
          String id = req.params(":id");
          catalogFramework.delete(new DeleteRequestImpl(id));
          return ImmutableMap.of("message", "Successfully deleted.");
        },
        util::getJson);

    get(
        "/enumerations/metacardtype/:type",
        APPLICATION_JSON,
        (req, res) -> {
          return util.getJson(enumExtractor.getEnumerations(req.params(":type")));
        });

    get(
        "/enumerations/attribute/:attribute",
        APPLICATION_JSON,
        (req, res) -> {
          return util.getJson(enumExtractor.getAttributeEnumerations(req.params(":attribute")));
        });

    get(
        "/localcatalogid",
        (req, res) ->
            String.format("{\"%s\":\"%s\"}", "local-catalog-id", catalogFramework.getId()));

    get("/writablesources", (req, res) -> getJsonWritableSources());

    post(
        "/transform/csv",
        APPLICATION_JSON,
        (req, res) -> {
          CsvTransform queryTransform = mapper.readValue(req.body(), CsvTransform.class);
          Map<String, Object> transformMap = mapper.parser().parseMap(req.body());
          queryTransform.setMetacards((List<Map<String, Object>>) transformMap.get("metacards"));

          List<Result> metacards =
              queryTransform
                  .getTransformedMetacards(types, attributeRegistry)
                  .stream()
                  .map(ResultImpl::new)
                  .collect(Collectors.toList());

          Set<String> matchedHiddenFields = Collections.emptySet();
          if (queryTransform.isApplyGlobalHidden()) {
            matchedHiddenFields = getHiddenFields(metacards);
          }

          SourceResponseImpl response =
              new SourceResponseImpl(null, metacards, Long.valueOf(metacards.size()));

          Map<String, Serializable> arguments =
              ImmutableMap.<String, Serializable>builder()
                  .put(
                      "hiddenFields",
                      new HashSet<>(
                          Sets.union(matchedHiddenFields, queryTransform.getHiddenFields())))
                  .put("columnOrder", new ArrayList<>(queryTransform.getColumnOrder()))
                  .put("aliases", new HashMap<>(queryTransform.getColumnAliasMap()))
                  .build();

          BinaryContent content = csvQueryResponseTransformer.transform(response, arguments);

          String acceptEncoding = req.headers("Accept-Encoding");
          // Very naive way to handle accept encoding, does not respect full spec
          boolean shouldGzip =
              StringUtils.isNotBlank(acceptEncoding)
                  && acceptEncoding.toLowerCase().contains("gzip");

          // Respond with content
          res.type("text/csv");
          String attachment =
              String.format("attachment;filename=export-%s.csv", Instant.now().toString());
          res.header("Content-Disposition", attachment);
          if (shouldGzip) {
            res.raw().addHeader("Content-Encoding", "gzip");
          }

          try ( //
          OutputStream servletOutputStream = res.raw().getOutputStream();
              InputStream resultStream = content.getInputStream()) {
            if (shouldGzip) {
              try (OutputStream gzipServletOutputStream =
                  new GZIPOutputStream(servletOutputStream)) {
                IOUtils.copy(resultStream, gzipServletOutputStream);
              }
            } else {
              IOUtils.copy(resultStream, servletOutputStream);
            }
          }
          return "";
        });

    after(
        (req, res) -> {
          res.type(APPLICATION_JSON);
        });

    exception(
        IngestException.class,
        (ex, req, res) -> {
          res.status(404);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          LOGGER.debug("Failed to ingest metacard", ex);
          res.body(util.getJson(ImmutableMap.of("message", UPDATE_ERROR_MESSAGE)));
        });

    exception(
        NotFoundException.class,
        (ex, req, res) -> {
          res.status(404);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          LOGGER.debug("Failed to find metacard.", ex);
          res.body(util.getJson(ImmutableMap.of("message", ex.getMessage())));
        });

    exception(
        NumberFormatException.class,
        (ex, req, res) -> {
          res.status(400);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(util.getJson(ImmutableMap.of("message", "Invalid values for numbers")));
        });

    exception(
        RuntimeException.class,
        (ex, req, res) -> {
          LOGGER.debug("Exception occurred.", ex);
          res.status(404);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(
              util.getJson(ImmutableMap.of("message", "Could not find what you were looking for")));
        });

    exception(
        SourceUnavailableException.class,
        (ex, req, res) -> {
          res.status(503);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          LOGGER.debug("A source was unavailable when trying to fetch", ex);
          res.body(
              util.getJson(
                  ImmutableMap.of(
                      "message",
                      "Could not connect to the sources at this time. Please try again later.")));
        });
  }

  private SourceInfoResponse getSources() throws SourceUnavailableException {
    SourceInfoRequestEnterprise sourceInfoRequestEnterprise =
        new SourceInfoRequestEnterprise(false);

    return catalogFramework.getSourceInfo(sourceInfoRequestEnterprise);
  }

  private Stream<String> extractPotentialBrokenAssociations(
      Set<String> idsToCheck, Metacard metacard) {
    if (!idsToCheck.contains(metacard.getId())) {
      // Any metacard not on the deleted list got pulled in because its pointing
      // to something being deleted
      return Stream.of(metacard.getId());
    } else {
      // Metacards on the delete list have their related and derived pulled
      return Stream.concat(
          getAttributes(metacard, Metacard.RELATED), getAttributes(metacard, Metacard.DERIVED));
    }
  }

  private Filter getParentAndChildrenFilter(String id) {
    Filter root = util.getFilterBuilder().attribute(Metacard.ID).is().equalTo().text(id);
    Filter derived = util.getFilterBuilder().attribute(Metacard.DERIVED).is().like().text(id);
    Filter related = util.getFilterBuilder().attribute(Metacard.RELATED).is().like().text(id);

    Filter parents = util.getFilterBuilder().anyOf(related, derived);

    return util.getFilterBuilder().anyOf(root, parents);
  }

  private Stream<String> getAttributes(Metacard metacard, String attribute) {
    return Optional.of(metacard)
        .map(m -> m.getAttribute(attribute))
        .filter(Objects::nonNull)
        .map(Attribute::getValues)
        .map(util::getStringList)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  private QueryRequestImpl createQueryForSource(Filter targetIds, SourceDescriptor sd) {
    QueryRequestImpl queryRequest =
        new QueryRequestImpl(
            new QueryImpl(targetIds, 1, 0, SortBy.NATURAL_ORDER, true, configuration.getTimeout()),
            Collections.singleton(sd.getSourceId()));
    queryRequest.getProperties().put("mode", "update");
    return queryRequest;
  }

  private String getJsonWritableSources() {
    return util.getJson(getWritableSources());
  }

  private Set<String> getHiddenFields(List<Result> metacards) {
    Set<String> matchedHiddenFields;
    List<Pattern> hiddenFieldPatterns =
        configuration
            .getHiddenAttributes()
            .stream()
            .map(Pattern::compile)
            .collect(Collectors.toList());
    matchedHiddenFields =
        metacards
            .stream()
            .map(Result::getMetacard)
            .map(Metacard::getMetacardType)
            .map(MetacardType::getAttributeDescriptors)
            .flatMap(Collection::stream)
            .map(AttributeDescriptor::getName)
            .filter(
                attr ->
                    hiddenFieldPatterns
                        .stream()
                        .map(Pattern::asPredicate)
                        .anyMatch(pattern -> pattern.test(attr)))
            .collect(Collectors.toSet());
    return matchedHiddenFields;
  }

  private void revertMetacard(Metacard versionMetacard, String id, boolean alreadyCreated)
      throws SourceUnavailableException, IngestException, FederationException,
          UnsupportedQueryException {
    LOGGER.trace("Reverting metacard [{}] to version [{}]", id, versionMetacard.getId());
    Metacard revertMetacard = MetacardVersionImpl.toMetacard(versionMetacard, types);
    Action action =
        Action.fromKey((String) versionMetacard.getAttribute(MetacardVersion.ACTION).getValue());

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

  private void revertContentandMetacard(Metacard latestContent, Metacard versionMetacard, String id)
      throws SourceUnavailableException, IngestException, ResourceNotFoundException, IOException,
          ResourceNotSupportedException, FederationException, UnsupportedQueryException {
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
            MetacardVersionImpl.toMetacard(versionMetacard, types));

    // Try to delete the "deleted metacard" marker first.
    boolean alreadyCreated = false;
    Action action =
        Action.fromKey((String) versionMetacard.getAttribute(MetacardVersion.ACTION).getValue());
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

  private void trySleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // Do nothing
    }
  }

  private void tryUpdate(int retries, Callable<Boolean> func)
      throws IngestException, SourceUnavailableException {
    if (retries <= 0) {
      throw new IngestException("Could not update metacard!");
    }
    LOGGER.trace("Trying to update metacard.");
    try {
      func.call();
      LOGGER.trace("Successfully updated metacard.");
    } catch (Exception e) {
      LOGGER.trace("Failed to update metacard");
      trySleep(350);
      tryUpdate(retries - 1, func);
    }
  }

  private void attemptDeleteDeletedMetacard(String id)
      throws UnsupportedQueryException, SourceUnavailableException, FederationException {
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
   * @return result of the callable func
   */
  private <T> T executeAsSystem(Callable<T> func) {
    Subject systemSubject = Security.runAsAdmin(() -> Security.getInstance().getSystemSubject());
    if (systemSubject == null) {
      throw new RuntimeException("Could not get systemSubject to version metacards.");
    }
    return systemSubject.execute(func);
  }

  private Instant getVersionedOnDate(Metacard mc) {
    return util.parseToDate(mc.getAttribute(MetacardVersion.VERSIONED_ON).getValue());
  }

  private AttributeDescriptor getDescriptor(Metacard target, String attribute) {
    return Optional.ofNullable(target)
        .map(Metacard::getMetacardType)
        .map(mt -> mt.getAttributeDescriptor(attribute))
        .orElseThrow(
            () -> new RuntimeException("Could not find attribute descriptor for: " + attribute));
  }

  protected UpdateResponse patchMetacards(List<MetacardChanges> metacardChanges)
      throws SourceUnavailableException, IngestException, FederationException,
          UnsupportedQueryException {
    Set<String> changedIds =
        metacardChanges.stream().flatMap(mc -> mc.getIds().stream()).collect(Collectors.toSet());

    Map<String, Result> results = util.getMetacards(changedIds, "*");

    for (MetacardChanges changeset : metacardChanges) {
      for (AttributeChange attributeChange : changeset.getAttributes()) {
        for (String id : changeset.getIds()) {
          List<String> values = attributeChange.getValues();

          Result result = results.get(id);
          if (result == null) {
            throw new IngestException(
                String.format("Metacard with id %s not found in result set", id));
          }

          Metacard metacard = result.getMetacard();

          Function<Serializable, Serializable> mapFunc = Function.identity();
          if (isChangeTypeDate(attributeChange, metacard)) {
            mapFunc = mapFunc.andThen(util::parseDate);
          }

          metacard.setAttribute(
              new AttributeImpl(
                  attributeChange.getAttribute(),
                  values
                      .stream()
                      .filter(Objects::nonNull)
                      .map(mapFunc)
                      .collect(Collectors.toList())));
        }
      }
    }

    List<Metacard> changedMetacards =
        results.values().stream().map(Result::getMetacard).collect(Collectors.toList());
    return catalogFramework.update(
        new UpdateRequestImpl(
            changedMetacards
                .stream()
                .map(Metacard::getId)
                .collect(Collectors.toList())
                .toArray(new String[0]),
            changedMetacards));
  }

  private boolean isChangeTypeDate(AttributeChange attributeChange, Metacard result) {
    return getDescriptor(result, attributeChange.getAttribute())
        .getType()
        .getAttributeFormat()
        .equals(AttributeType.AttributeFormat.DATE);
  }

  private List<Result> getMetacardHistory(String id) throws Exception {
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

  private Metacard updateMetacard(String id, Metacard metacard)
      throws SourceUnavailableException, IngestException {
    return catalogFramework
        .update(new UpdateRequestImpl(id, metacard))
        .getUpdatedMetacards()
        .get(0)
        .getNewMetacard();
  }

  private Metacard saveMetacard(Metacard metacard)
      throws IngestException, SourceUnavailableException {
    return catalogFramework.create(new CreateRequestImpl(metacard)).getCreatedMetacards().get(0);
  }

  private List<String> getWritableSources() {
    List<String> stringList =
        catalogStores.stream().map(CatalogStore::getId).collect(Collectors.toList());
    stringList.add(catalogFramework.getId());
    return stringList;
  }

  private static class ByteSourceWrapper extends ByteSource {
    Supplier<InputStream> supplier;

    ByteSourceWrapper(Supplier<InputStream> supplier) {
      this.supplier = supplier;
    }

    @Override
    public InputStream openStream() throws IOException {
      return supplier.get();
    }
  }
}
