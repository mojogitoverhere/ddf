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

import ddf.catalog.transform.CatalogTransformerException;
import ddf.mime.MimeTypeResolutionException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.activation.MimeTypeParseException;

/** This class assumes that the extracted archive format has already been validated. */
class ExtractedArchive {

  interface Visitor {

    void visitMetacardXml(Path metacardXmlFile) throws IOException, CatalogTransformerException;

    void visitContent(Path contentFile)
        throws FileNotFoundException, MimeTypeResolutionException, MimeTypeParseException;

    void visitDerivedContent(String type, Path derivedContentFile)
        throws FileNotFoundException, MimeTypeResolutionException, MimeTypeParseException;

    void visitHistoryMetacardXml(Path metacardXmlFile)
        throws IOException, CatalogTransformerException;

    void visitHistoryDerivedContent(String type, Path derivedContentFile)
        throws MimeTypeResolutionException, MimeTypeParseException, FileNotFoundException;

    void visitHistoryContent(Path contentFile)
        throws MimeTypeResolutionException, MimeTypeParseException, FileNotFoundException;
  }

  private Path temporaryDirectory;

  ExtractedArchive(Path temporaryDirectory) {
    this.temporaryDirectory = temporaryDirectory;
  }

  <T extends Visitor> T acceptVisitor(T visitor)
      throws IOException, CatalogTransformerException, MimeTypeResolutionException,
          MimeTypeParseException {

    Path metacards = Paths.get(temporaryDirectory.toString(), "metacards");
    List<Path> threeDigitDirectories =
        listFiles(metacards, path -> path.getFileName().toString().length() == 3);

    for (Path threeDigitDirectory : threeDigitDirectories) {
      List<Path> threeDigitSubdirectories = listFiles(threeDigitDirectory);

      for (Path threeDigitSubdirectory : threeDigitSubdirectories) {
        visitMetacardAndDerivedDirectories(
            threeDigitSubdirectory,
            visitor::visitMetacardXml,
            visitor::visitContent,
            visitor::visitDerivedContent);

        Path historyFile = Paths.get(threeDigitSubdirectory.toString(), "history");
        if (Files.exists(historyFile)) {
          List<Path> histories = listFiles(historyFile);
          for (Path history : histories) {
            visitMetacardAndDerivedDirectories(
                history,
                visitor::visitHistoryMetacardXml,
                visitor::visitHistoryContent,
                visitor::visitHistoryDerivedContent);
          }
        }
      }
    }

    return visitor;
  }

  int getMetacardCount() throws ImportException {
    try {
      return acceptVisitor(new TotalWorkVisitor()).getCount();
    } catch (IOException
        | CatalogTransformerException
        | MimeTypeParseException
        | MimeTypeResolutionException e) {
      throw new ImportException(
          "Failed to get the number of metacards from the extracted import archive.", e);
    }
  }

  private List<Path> listFiles(Path root) throws IOException {
    return listFiles(root, path -> true);
  }

  private List<Path> listFiles(Path root, Predicate<Path> pathFilter) throws IOException {
    return Files.walk(root, 1)
        .filter(path -> !path.equals(root))
        .filter(pathFilter)
        .collect(Collectors.toList());
  }

  private void visitMetacardAndDerivedDirectories(
      Path metacardIdDirectory,
      PathConsumer metacardPathConsumer,
      PathConsumer contentPathConsumer,
      TypeAndPathConsumer derivedContentFileConsumer)
      throws IOException, CatalogTransformerException, MimeTypeResolutionException,
          MimeTypeParseException {

    Path metacardXmlFile =
        Paths.get(
            metacardIdDirectory.toString(),
            "metacard",
            metacardIdDirectory.getFileName().toString() + ".xml");

    metacardPathConsumer.consume(metacardXmlFile);

    Path contentDirectory = Paths.get(metacardIdDirectory.toString(), "content");
    if (Files.exists(contentDirectory)) {
      List<Path> contentFiles = listFiles(contentDirectory);
      contentPathConsumer.consume(contentFiles.get(0));
    }

    Path derivedFile = Paths.get(metacardIdDirectory.toString(), "derived");
    if (Files.exists(derivedFile)) {
      List<Path> derivedDirectories = listFiles(derivedFile);

      for (Path derivedDirectory : derivedDirectories) {
        List<Path> derivedContentFiles = listFiles(derivedDirectory);
        derivedContentFileConsumer.consume(
            derivedDirectory.getFileName().toString(), derivedContentFiles.get(0));
      }
    }
  }

  private interface PathConsumer {
    void consume(Path metacardPath)
        throws IOException, CatalogTransformerException, MimeTypeResolutionException,
            MimeTypeParseException;
  }

  private interface TypeAndPathConsumer {
    void consume(String type, Path contentFile)
        throws IOException, MimeTypeResolutionException, MimeTypeParseException;
  }
}
