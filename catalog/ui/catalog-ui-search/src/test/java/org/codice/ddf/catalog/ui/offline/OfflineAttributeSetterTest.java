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
package org.codice.ddf.catalog.ui.offline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.data.impl.types.OfflineAttributes;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Offline;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.security.SubjectUtils;
import ddf.security.assertion.SecurityAssertion;
import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;

@RunWith(MockitoJUnitRunner.class)
public class OfflineAttributeSetterTest {

  @Mock private CatalogFramework catalogFramework;

  private static final String COMMENT = "This is a comment.";

  private static final String METACARD_ID = "0123456789";

  private static final String OUTPUT_PATH = METACARD_ID + ".zip";

  private static final String WHOAMI = "admin@local";

  private static final String SOURCE_ID = "TheSourceId";

  private OfflineAttributeSetter attributeSetter;

  @Before
  public void setup() throws SourceUnavailableException, IngestException {
    setupMockSubject();
    setupMockUpdateResponse();

    attributeSetter = new OfflineAttributeSetter(catalogFramework);
  }

  @Test
  public void testProcess() throws SourceUnavailableException, IngestException {
    URI overviewUri = URI.create("content://mycontent#overview");
    URI originalUri = URI.create("content://mycontent#original");

    Metacard metacard =
        createMetacard(
            METACARD_ID,
            Collections.emptyList(),
            originalUri,
            "https://my.content.com",
            Collections.singletonList(overviewUri),
            Collections.singletonList("https://my.derived.com"));
    attributeSetter.process(metacard, COMMENT, OUTPUT_PATH);

    assertOfflineAttributes(metacard);
    verify(catalogFramework, times(1)).update(any(UpdateRequest.class));
  }

  @Test
  public void testProcessDoesNotModifyRevisions()
      throws SourceUnavailableException, IngestException {
    URI overviewUri = URI.create("content://mycontent#overview");
    URI originalUri = URI.create("content://mycontent#original");

    Metacard metacard =
        createMetacard(
            METACARD_ID,
            Collections.singletonList("revision"),
            originalUri,
            "https://my.content.com",
            Collections.singletonList(overviewUri),
            Collections.singletonList("https://my.derived.com"));
    attributeSetter.process(metacard, COMMENT, OUTPUT_PATH);

    assertOfflineAttributesForHistory(metacard);
    verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
  }

  private void assertOfflineAttributes(Metacard metacard) {
    assertThat(metacard.getAttribute(Offline.OFFLINE_COMMENT).getValue(), is(COMMENT));
    assertThat(
        metacard.getAttribute(Offline.OFFLINE_LOCATION_PATH).getValue(), is(METACARD_ID + ".zip"));
    assertThat(metacard.getAttribute(Offline.OFFLINE_DATE).getValue(), notNullValue());
    assertThat(metacard.getAttribute(Offline.OFFLINED_BY).getValue(), is(WHOAMI));

    assertThat(metacard.getAttribute(Core.RESOURCE_URI), nullValue());
    assertThat(metacard.getAttribute(Core.RESOURCE_DOWNLOAD_URL), nullValue());
    assertThat(metacard.getAttribute(Core.DERIVED_RESOURCE_URI), nullValue());
    assertThat(metacard.getAttribute(Core.DERIVED_RESOURCE_DOWNLOAD_URL), nullValue());
  }

  private void assertOfflineAttributesForHistory(Metacard metacard) {
    assertThat(metacard.getAttribute(Offline.OFFLINE_COMMENT), nullValue());
    assertThat(metacard.getAttribute(Offline.OFFLINE_LOCATION_PATH), nullValue());
    assertThat(metacard.getAttribute(Offline.OFFLINE_DATE), nullValue());
    assertThat(metacard.getAttribute(Offline.OFFLINED_BY), nullValue());

    assertThat(metacard.getAttribute(Core.RESOURCE_URI), notNullValue());
    assertThat(metacard.getAttribute(Core.RESOURCE_DOWNLOAD_URL), notNullValue());
    assertThat(metacard.getAttribute(Core.DERIVED_RESOURCE_URI), notNullValue());
    assertThat(metacard.getAttribute(Core.DERIVED_RESOURCE_DOWNLOAD_URL), notNullValue());
  }

  private void setupMockSubject() {
    Subject subject = mock(Subject.class);
    PrincipalCollection principalCollection = mock(PrincipalCollection.class);
    when(subject.getPrincipals()).thenReturn(principalCollection);
    SecurityAssertion securityAssertion = mock(SecurityAssertion.class);
    when(principalCollection.oneByType(any())).thenReturn(securityAssertion);
    AttributeStatement attributeStatement = mock(AttributeStatement.class);
    when(securityAssertion.getAttributeStatements())
        .thenReturn(Collections.singletonList(attributeStatement));
    Attribute attribute = mock(Attribute.class);
    when(attributeStatement.getAttributes()).thenReturn(Collections.singletonList(attribute));
    when(attribute.getName()).thenReturn(SubjectUtils.EMAIL_ADDRESS_CLAIM_URI);
    XSString xsString = mock(XSString.class);
    when(xsString.getValue()).thenReturn(WHOAMI);
    when(attribute.getAttributeValues()).thenReturn(Collections.singletonList(xsString));
    ThreadContext.bind(subject);
  }

  private void setupMockUpdateResponse() throws SourceUnavailableException, IngestException {
    UpdateResponse updateResponse = mock(UpdateResponse.class);
    when(updateResponse.getProcessingErrors()).thenReturn(Collections.emptySet());
    when(catalogFramework.update(any(UpdateRequest.class))).thenReturn(updateResponse);
  }

  private Metacard createMetacard(
      String metacardId,
      List<String> tags,
      URI resourceUri,
      String resourceDownloadUrl,
      List<URI> derivedResourceUris,
      List<Serializable> derivedResourceUrls) {
    Metacard metacard =
        new MetacardImpl(
            new MetacardTypeImpl(
                "offline-type", Arrays.asList(new OfflineAttributes(), new CoreAttributes())));
    metacard.setSourceId(SOURCE_ID);
    metacard.setAttribute(new AttributeImpl(Core.RESOURCE_URI, resourceUri.toString()));
    metacard.setAttribute(new AttributeImpl(Core.RESOURCE_DOWNLOAD_URL, resourceDownloadUrl));
    metacard.setAttribute(new AttributeImpl(Core.ID, metacardId));
    metacard.setAttribute(
        new AttributeImpl(
            Core.DERIVED_RESOURCE_URI,
            derivedResourceUris.stream().map(URI::toString).collect(Collectors.toList())));
    metacard.setAttribute(
        new AttributeImpl(Core.DERIVED_RESOURCE_DOWNLOAD_URL, derivedResourceUrls));
    metacard.setAttribute(new AttributeImpl(Metacard.TAGS, (Serializable) tags));
    return metacard;
  }
}
