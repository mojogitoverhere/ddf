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
package org.codice.ddf.commands.catalog;

import com.google.common.collect.ImmutableSet;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.operation.impl.DeleteStorageRequestImpl;
import ddf.catalog.core.versioning.DeletedMetacard;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.ResourceRequestByProductUri;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.codice.ddf.catalog.transformer.zip.JarSigner;
import org.codice.ddf.commands.catalog.export.ExportItem;
import org.codice.ddf.commands.catalog.export.IdAndUriMetacard;
import org.codice.ddf.commands.util.CatalogCommandRuntimeException;
import org.codice.ddf.commands.util.QueryResulterable;
import org.fusesource.jansi.Ansi;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Experimental. */
@Service
@Command(
  scope = CatalogCommands.NAMESPACE,
  name = "export",
  description = "Exports Metacards and history from the current Catalog"
)
public class ExportCommand extends CqlCommands {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportCommand.class);

  private static final SimpleDateFormat ISO_8601_DATE_FORMAT;

  static {
    ISO_8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'");
    ISO_8601_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private static final Supplier<String> FILE_NAMER =
      () -> "export-" + ISO_8601_DATE_FORMAT.format(Date.from(Instant.now())) + ".zip";

  private static final int PAGE_SIZE = 64;

  private MetacardTransformer transformer;

  private Filter revisionFilter;

  private JarSigner jarSigner = new JarSigner();

  @Reference private StorageProvider storageProvider;

  @Option(
    name = "--output",
    description =
        "Output file to export Metacards and contents into. Paths are absolute and must be in quotes. Will default to auto generated name inside of ddf.home",
    multiValued = false,
    required = false,
    aliases = {"-o"}
  )
  String output = Paths.get(System.getProperty("ddf.home"), FILE_NAMER.get()).toString();

  @Option(
    name = "--location",
    description = "Location to list in the tombstone metacard left after export",
    multiValued = false,
    required = false
  )
  String location = null;

  @Option(
    name = "--delete",
    required = true,
    aliases = {"-d", "delete"},
    multiValued = false,
    description = "Flag to delete Metacards and content after export"
  )
  boolean delete = false;

  @Option(
    name = "--deleted",
    required = false,
    aliases = {"-a", "deleted"},
    multiValued = false,
    description = "Equivalent to --cql \"\\\"metacard-tags\\\" like 'deleted'\""
  )
  boolean deleted = false;

  @Option(
    name = "--force",
    required = false,
    aliases = {"-f"},
    multiValued = false,
    description = "Do not prompt"
  )
  boolean force = false;

  @Option(
    name = "--skip-signature-verification",
    required = false,
    multiValued = false,
    description =
        "Produces the export zip but does NOT sign the resulting zip file. This file will not be able to be verified on import for integrity and security."
  )
  boolean unsafe = false;

  @Option(
    name = "--skip-tombstone-creation",
    required = false,
    multiValued = false,
    description = "Produces the Export zip but does NOT upload a tombstone metacard."
  )
  boolean skipTombstoneCreation = false;

  @Override
  protected Object executeWithSubject() throws Exception {
    Filter filter = getFilter();
    transformer =
        getServiceByFilter(
                MetacardTransformer.class, String.format("(%s=%s)", "id", DEFAULT_TRANSFORMER_ID))
            .orElseThrow(
                () ->
                    new CatalogCommandRuntimeException(
                        "Could not get " + DEFAULT_TRANSFORMER_ID + " transformer"));
    revisionFilter = initRevisionFilter();

    final File outputFile = initOutputFile(output);
    if (outputFile.exists()) {
      printErrorMessage(String.format("File [%s] already exists!", outputFile.getPath()));
      return null;
    }

    final File parentDirectory = outputFile.getParentFile();
    if (parentDirectory == null || !parentDirectory.isDirectory()) {
      printErrorMessage(String.format("Directory [%s] must exist.", output));
      console.println("If the directory does indeed exist, try putting the path in quotes.");
      return null;
    }

    String filename = FilenameUtils.getName(outputFile.getPath());
    if (StringUtils.isBlank(filename) || !filename.endsWith(".zip")) {
      console.println("Filename must end with '.zip' and not be blank");
      return null;
    }

    if (location == null && !force) {
      console.println("Please enter the export location (for the archive record): ");
      location = getUserInputModifiable().toString();
    }

    if (delete && !force) {
      console.println(
          "This action will remove all exported metacards and content from the catalog. Are you sure you wish to continue? (y/N):");
      String input = getUserInputModifiable().toString();
      if (!input.matches("^[yY][eE]?[sS]?$")) {
        console.println("ABORTED EXPORT.");
        return null;
      }
    }

    SecurityLogger.audit("Called catalog:export command with path : {}", output);

    ZipFile zipFile = new ZipFile(outputFile);

    console.println("Starting metacard export...");
    Instant start = Instant.now();
    List<ExportItem> exportedItems = doMetacardExport(zipFile, filter);
    console.println("Metacards exported in: " + getFormattedDuration(start));

    console.println("Number of metacards exported: " + exportedItems.size());
    console.println();

    SecurityLogger.audit(
        "Ids of exported metacards and content:\n{}",
        exportedItems
            .stream()
            .map(ExportItem::getId)
            .distinct()
            .collect(Collectors.joining(", ", "[", "]")));

    console.println("Starting content export...");
    start = Instant.now();
    List<ExportItem> exportedContentItems = doContentExport(zipFile, exportedItems);
    console.println("Content exported in: " + getFormattedDuration(start));
    console.println("Number of content exported: " + exportedContentItems.size());

    console.println();

    if (delete) {
      doDelete(exportedItems, exportedContentItems);
    }

    if (!unsafe) {
      SecurityLogger.audit("Signing exported data. file: [{}]", zipFile.getFile().getName());
      console.println("Signing zip file...");
      start = Instant.now();
      jarSigner.signJar(
          zipFile.getFile(),
          System.getProperty("org.codice.ddf.system.hostname"),
          System.getProperty("javax.net.ssl.keyStorePassword"),
          System.getProperty("javax.net.ssl.keyStore"),
          System.getProperty("javax.net.ssl.keyStorePassword"));
      console.println("zip file signed in: " + getFormattedDuration(start));
    }

    if (!skipTombstoneCreation) {
      console.println();
      console.println("Uploading tombstone metacard to catalog");
      ArrayList<String> tombstoneData =
          exportedItems
              .stream()
              .filter(ei -> !"revision".equals(ei.getMetacardTag()))
              .map(ExportItem::tombstoneString)
              .collect(Collectors.toCollection(ArrayList::new));
      ExportedMetacard tombstone = new ExportedMetacard(tombstoneData, location, Instant.now());
      tombstone.setTitle(zipFile.getFile().getName());
      try {
        catalogFramework.create(new CreateRequestImpl(tombstone));
      } catch (IngestException | SourceUnavailableException e) {
        String msg = "Could not create the tombstone metacard";
        LOGGER.error(msg, e);
        printErrorMessage(msg);
        console.println();
        console.println("PARTIAL Export complete. [Tombstone metacard not created]");
        console.println(
            "Configuration may be preventing the current user from metacard creation. Please ensure all permissions are correct.");
        console.println("Exported to: " + zipFile.getFile().getCanonicalPath());
        return null;
      }
    }
    console.println();
    console.println("Export complete.");
    console.println("Exported to: " + zipFile.getFile().getCanonicalPath());
    return null;
  }

  private File initOutputFile(String output) {
    String resolvedOutput;
    File initialOutputFile = new File(output);
    if (initialOutputFile.isDirectory()) {
      // If directory was specified, auto generate file name
      resolvedOutput = Paths.get(initialOutputFile.getPath(), FILE_NAMER.get()).toString();
    } else {
      resolvedOutput = output;
    }

    return new File(resolvedOutput);
  }

  private List<ExportItem> doMetacardExport(/*Mutable,IO*/ ZipFile zipFile, Filter filter) {
    Set<String> seenIds = new HashSet<>(1024);
    Set<String> seenAssociations = new HashSet<>(1024);
    List<ExportItem> exportedItems = new ArrayList<>();
    for (Result result :
        new QueryResulterable(catalogFramework, (i) -> getQuery(filter, i, PAGE_SIZE), PAGE_SIZE)) {
      if (!seenIds.contains(result.getMetacard().getId())) {
        writeToZip(zipFile, result);
        exportedItems.add(
            new ExportItem(
                result.getMetacard().getId(),
                getTag(result),
                result.getMetacard().getResourceURI(),
                getDerivedResources(result),
                result.getMetacard().getTitle()));
        seenIds.add(result.getMetacard().getId());
        seenAssociations.addAll(getAssociations(result));
      }

      // Fetch and export all history for each exported item
      for (Result revision :
          new QueryResulterable(
              catalogFramework,
              (i) -> getQuery(getHistoryFilter(result), i, PAGE_SIZE),
              PAGE_SIZE)) {
        if (seenIds.contains(revision.getMetacard().getId())) {
          continue;
        }
        writeToZip(zipFile, revision);
        exportedItems.add(
            new ExportItem(
                revision.getMetacard().getId(),
                getTag(revision),
                revision.getMetacard().getResourceURI(),
                getDerivedResources(result),
                result.getMetacard().getTitle()));
        seenIds.add(revision.getMetacard().getId());
        seenAssociations.addAll(getAssociations(result));
      }
    }
    if (delete && seenAssociations.stream().parallel().anyMatch(seenIds::contains)) {
      console.println(
          "This export will break some associations currently in the catalog. Do you wish to continue? (yes/NO):");
      Scanner scanner = new Scanner(session.getKeyboard(), "utf-8");
      String userInput = scanner.nextLine();
      if (userInput.matches("^$|^[nN][oO]?$")) {
        throw new CatalogCommandRuntimeException("ABORTED EXPORT.");
      }
    }
    return exportedItems;
  }

  private List<String> getAssociations(Result result) {
    return Stream.concat( //
            getAssociationAttributes(result, Metacard.DERIVED),
            getAssociationAttributes(result, Metacard.RELATED))
        .map(String::valueOf)
        .collect(Collectors.toList());
  }

  private Stream<Serializable> getAssociationAttributes(Result result, String attribute) {
    return Optional.ofNullable(result.getMetacard())
        .map(m -> m.getAttribute(attribute))
        .map(Attribute::getValues)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  private List<String> getDerivedResources(Result result) {
    if (result.getMetacard().getAttribute(Metacard.DERIVED_RESOURCE_URI) == null) {
      return Collections.emptyList();
    }

    return result
        .getMetacard()
        .getAttribute(Metacard.DERIVED_RESOURCE_URI)
        .getValues()
        .stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .collect(Collectors.toList());
  }

  private List<ExportItem> doContentExport(
      /*Mutable,IO*/ ZipFile zipFile, List<ExportItem> exportedItems) throws ZipException {
    List<ExportItem> contentItemsToExport =
        exportedItems
            .stream()
            // Only things with a resource URI
            .filter(ei -> ei.getResourceUri() != null)
            // Only our content scheme
            .filter(ei -> ei.getResourceUri().getScheme() != null)
            .filter(ei -> ei.getResourceUri().getScheme().startsWith(ContentItem.CONTENT_SCHEME))
            // Deleted Metacards have no content associated
            .filter(ei -> !ei.getMetacardTag().equals("deleted"))
            // for revision metacards, only those that have their own content
            .filter(
                ei ->
                    !ei.getMetacardTag().equals("revision")
                        || ei.getResourceUri().getSchemeSpecificPart().equals(ei.getId()))
            .filter(distinctByKey(ei -> ei.getResourceUri().getSchemeSpecificPart()))
            .collect(Collectors.toList());

    List<ExportItem> exportedContentItems = new ArrayList<>();
    for (ExportItem contentItem : contentItemsToExport) {
      ResourceResponse resource;
      try {
        resource =
            catalogFramework.getLocalResource(
                new ResourceRequestByProductUri(contentItem.getResourceUri()));
      } catch (IOException | ResourceNotSupportedException e) {
        throw new CatalogCommandRuntimeException(
            "Unable to retrieve resource for " + contentItem.getId(), e);
      } catch (ResourceNotFoundException e) {
        continue;
      }
      writeToZip(zipFile, contentItem, resource);
      exportedContentItems.add(contentItem);
      if (!contentItem.getMetacardTag().equals("revision")) {
        for (String derivedUri : contentItem.getDerivedUris()) {
          URI uri;
          try {
            uri = new URI(derivedUri);
          } catch (URISyntaxException e) {
            LOGGER.debug(
                "Uri [{}] is not a valid URI. Derived content will not be included in export",
                derivedUri);
            continue;
          }

          ResourceResponse derivedResource;
          try {
            derivedResource =
                catalogFramework.getLocalResource(new ResourceRequestByProductUri(uri));
          } catch (IOException e) {
            throw new CatalogCommandRuntimeException(
                "Unable to retrieve resource for " + contentItem.getId(), e);
          } catch (ResourceNotFoundException | ResourceNotSupportedException e) {
            LOGGER.warn("Could not retreive resource [{}]", uri, e);
            console.printf(
                "%sUnable to retrieve resource for export : %s%s%n",
                Ansi.ansi().fg(Ansi.Color.RED).toString(), uri, Ansi.ansi().reset().toString());
            continue;
          }
          writeToZip(zipFile, contentItem, derivedResource);
        }
      }
    }
    return exportedContentItems;
  }

  private void doDelete(List<ExportItem> exportedItems, List<ExportItem> exportedContentItems) {
    Instant start;
    console.println("Starting delete");
    start = Instant.now();
    for (ExportItem exportedContentItem : exportedContentItems) {
      try {
        DeleteStorageRequestImpl deleteRequest =
            new DeleteStorageRequestImpl(
                Collections.singletonList(
                    new IdAndUriMetacard(
                        exportedContentItem.getId(), exportedContentItem.getResourceUri())),
                exportedContentItem.getId(),
                Collections.emptyMap());
        storageProvider.delete(deleteRequest);
        storageProvider.commit(deleteRequest);
      } catch (StorageException e) {
        printErrorMessage(
            "Could not delete content for metacard: " + exportedContentItem.toString());
      }
    }
    for (ExportItem exported : exportedItems) {
      try {
        catalogProvider.delete(new DeleteRequestImpl(exported.getId()));
      } catch (IngestException e) {
        printErrorMessage("Could not delete metacard: " + exported.toString());
      }
    }

    // delete items from cache
    try {
      getCacheProxy()
          .removeById(
              exportedItems
                  .stream()
                  .map(ExportItem::getId)
                  .collect(Collectors.toList())
                  .toArray(new String[exportedItems.size()]));
    } catch (Exception e) {
      LOGGER.warn(
          "Could not delete all exported items from cache (Results will eventually expire)", e);
    }

    console.println("Metacards and Content deleted in: " + getFormattedDuration(start));
    console.println("Number of metacards deleted: " + exportedItems.size());
    console.println("Number of content deleted: " + exportedContentItems.size());
  }

  private void writeToZip(
      /*Mutable,IO*/ ZipFile zipFile, ExportItem exportItem, ResourceResponse resource)
      throws ZipException {
    ZipParameters parameters = new ZipParameters();
    parameters.setSourceExternalStream(true);
    String id = exportItem.getId();
    String path = getContentPath(id, resource);
    parameters.setFileNameInZip(path);
    zipFile.addStream(resource.getResource().getInputStream(), parameters);
  }

  private String getContentPath(String id, ResourceResponse resource) {
    String path = Paths.get("metacards", id.substring(0, 3), id).toString();
    String fragment = ((URI) resource.getRequest().getAttributeValue()).getFragment();

    if (fragment == null) { // is root content, put in root id folder
      path = Paths.get(path, "content", resource.getResource().getName()).toString();
    } else { // is derived content, put in subfolder
      path = Paths.get(path, "derived", fragment, resource.getResource().getName()).toString();
    }
    return path;
  }

  private void writeToZip(/*Mutable,IO*/ ZipFile zipFile, Result result) {
    ZipParameters parameters = new ZipParameters();
    parameters.setSourceExternalStream(true);
    String id = result.getMetacard().getId();
    parameters.setFileNameInZip(
        Paths.get("metacards", id.substring(0, 3), id, "metacard", id + ".xml").toString());

    try {
      BinaryContent binaryMetacard =
          transformer.transform(result.getMetacard(), Collections.emptyMap());
      zipFile.addStream(binaryMetacard.getInputStream(), parameters);
    } catch (ZipException e) {
      LOGGER.error("Error processing result and adding to ZIP", e);
      throw new CatalogCommandRuntimeException(e);
    } catch (CatalogTransformerException e) {
      LOGGER.warn(
          "Could not transform metacard. Metacard will not be added to zip [{}]",
          result.getMetacard().getId());
      console.printf(
          "%sCould not transform metacard. Metacard will not be included in export. %s - %s%s%n",
          Ansi.ansi().fg(Ansi.Color.RED).toString(),
          result.getMetacard().getId(),
          result.getMetacard().getTitle(),
          Ansi.ansi().reset().toString());
    }
  }

  /**
   * Generates stateful predicate to filter distinct elements by a certain key in the object.
   *
   * @param keyExtractor Function to pull the desired key out of the object
   * @return the stateful predicate
   */
  private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  private String getTag(Result r) {
    Set<String> tags = r.getMetacard().getTags();
    if (tags.contains("deleted")) {
      return "deleted";
    } else if (tags.contains("revision")) {
      return "revision";
    } else {
      return "nonhistory";
    }
  }

  private Filter initRevisionFilter() {
    return filterBuilder.attribute(Metacard.TAGS).is().like().text("revision");
  }

  private Filter getHistoryFilter(Result result) {
    String id;
    String typeName = result.getMetacard().getMetacardType().getName();
    switch (typeName) {
      case DeletedMetacard.PREFIX:
        id = String.valueOf(result.getMetacard().getAttribute("metacard.deleted.id").getValue());
        break;
      case MetacardVersion.PREFIX:
        return null;
      default:
        id = result.getMetacard().getId();
        break;
    }

    return filterBuilder.allOf(
        revisionFilter, filterBuilder.attribute("metacard.version.id").is().equalTo().text(id));
  }

  protected Filter getFilter() throws InterruptedException, ParseException, CQLException {
    Filter filter = super.getFilter();
    if (deleted) {
      filter =
          filterBuilder.allOf(
              filter, filterBuilder.attribute(Metacard.TAGS).is().like().text("deleted"));
    }
    return filter;
  }

  private QueryRequestImpl getQuery(Filter filter, int index, int pageSize) {
    return new QueryRequestImpl(
        new QueryImpl(
            filter, index, pageSize, SortBy.NATURAL_ORDER, false, TimeUnit.MINUTES.toMillis(1)),
        new HashMap<>());
  }

  static class ExportedMetacard extends MetacardImpl {
    static final String EXPORTED_DATA = "exported.data";

    static final String EXPORTED_LOCATION = "exported.location";

    static final String EXPORTED_TIME = "exported.time";

    static final Set<AttributeDescriptor> ATTRIBUTE_DESCRIPTORS =
        ImmutableSet.of(
            new AttributeDescriptorImpl(
                EXPORTED_DATA, true, true, false, true, BasicTypes.STRING_TYPE),
            new AttributeDescriptorImpl(
                EXPORTED_LOCATION, true, true, false, false, BasicTypes.STRING_TYPE),
            new AttributeDescriptorImpl(
                EXPORTED_TIME, true, true, false, false, BasicTypes.DATE_TYPE));

    static final MetacardType METACARD_TYPE =
        new MetacardTypeImpl(
            "exported.metacards", BasicTypes.BASIC_METACARD, ATTRIBUTE_DESCRIPTORS);

    static {
      Bundle bundle = FrameworkUtil.getBundle(ExportCommand.class);
      BundleContext context = bundle == null ? null : bundle.getBundleContext();
      if (bundle == null || context == null) {
        LOGGER.error("Could not get bundle to register ExportMetacard types!");
      } else {
        context.registerService(MetacardType.class, METACARD_TYPE, new Hashtable<>());
      }
    }

    <T extends Serializable> ExportedMetacard(T data, String location, Instant time) {
      super(METACARD_TYPE);
      setAttribute(EXPORTED_DATA, data);
      setAttribute(EXPORTED_LOCATION, location);
      setAttribute(EXPORTED_TIME, Date.from(time));
    }
  }
}
