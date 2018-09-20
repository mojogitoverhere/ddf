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

import com.google.common.io.Files;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.BinaryContentImpl;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import ddf.mime.MimeTypeMapper;
import ddf.mime.MimeTypeResolutionException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

class ImportFileVisitor implements ExtractedArchive.Visitor {

  private List<MetacardImportItem> metacardImportItems = new LinkedList<>();

  private MetacardImportItem metacardImportItemInProgress = null;

  private final InputTransformer inputTransformer;

  private final MimeTypeMapper mimeTypeMapper;

  ImportFileVisitor(InputTransformer inputTransformer, MimeTypeMapper mimeTypeMapper) {
    this.inputTransformer = inputTransformer;
    this.mimeTypeMapper = mimeTypeMapper;
  }

  List<MetacardImportItem> getMetacardImportItems() {
    return metacardImportItems;
  }

  @Override
  public void visitMetacardXml(Path metacardXmlFile)
      throws IOException, CatalogTransformerException {
    flush();
    metacardImportItemInProgress = createMetacardImportItem(metacardXmlFile);
  }

  @Override
  public void visitContent(Path contentFile)
      throws FileNotFoundException, MimeTypeResolutionException, MimeTypeParseException {
    metacardImportItemInProgress.setContentImportItem(createContentImportItem(contentFile));
  }

  @Override
  public void visitDerivedContent(String type, Path derivedContentFile)
      throws FileNotFoundException, MimeTypeResolutionException, MimeTypeParseException {
    metacardImportItemInProgress
        .getDerivedContent()
        .put(type, createContentImportItem(derivedContentFile));
  }

  ImportFileVisitor flush() {
    if (metacardImportItemInProgress != null) {
      metacardImportItems.add(metacardImportItemInProgress);
      metacardImportItemInProgress = null;
    }
    return this;
  }

  private MetacardImportItem.ContentImportItem createContentImportItem(Path file)
      throws MimeTypeResolutionException, MimeTypeParseException, FileNotFoundException {
    MimeType mimeType =
        new MimeType(
            mimeTypeMapper.getMimeTypeForFileExtension(Files.getFileExtension(file.toString())));
    BinaryContent binaryContent =
        new BinaryContentImpl(new FileInputStream(file.toFile()), mimeType);
    return new MetacardImportItem.ContentImportItem(binaryContent, file.getFileName().toString());
  }

  private MetacardImportItem createMetacardImportItem(Path xmlFile)
      throws IOException, CatalogTransformerException {
    MetacardImportItem metacardImportItem = new MetacardImportItem();

    try (InputStream inputStream = new FileInputStream(xmlFile.toFile())) {
      Metacard metacard = inputTransformer.transform(inputStream);
      metacardImportItem.setMetacard(metacard);
    }

    return metacardImportItem;
  }
}
