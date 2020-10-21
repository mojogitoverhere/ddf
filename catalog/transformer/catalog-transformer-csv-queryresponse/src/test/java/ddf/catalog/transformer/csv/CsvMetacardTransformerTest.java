/*
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */

package ddf.catalog.transformer.csv;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeType;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.transform.CatalogTransformerException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.codice.ddf.catalog.ui.alias.AttributeAliases;
import org.junit.Before;
import org.junit.Test;

public class CsvMetacardTransformerTest {

  private Map<String, Serializable> arguments;

  private CsvMetacardTransformer transformer;

  private Metacard normalMC, nullMC;

  private static final Set<AttributeDescriptor> ATTRIBUTE_DESCRIPTORS =
      ImmutableSet.of(
          buildAttributeDescriptor("stringAtt", BasicTypes.STRING_TYPE),
          buildAttributeDescriptor("intAtt", BasicTypes.INTEGER_TYPE),
          buildAttributeDescriptor("doubleAtt", BasicTypes.DOUBLE_TYPE));

  private static final List<String> VALUES = ImmutableList.of("stringVal", "101", "3.14159");

  private static final List<Attribute> ATTRIBUTES =
      ImmutableList.of(
          new AttributeImpl("stringAtt", "stringVal"),
          new AttributeImpl("intAtt", 101),
          new AttributeImpl("doubleAtt", 3.14159));

  @Before
  public void setUp() {
    AttributeAliases aliases = getAliases();
    this.transformer = new CsvMetacardTransformer(aliases);
    this.arguments = new HashMap<>();
    // This ordering should be different than {@code ATTRIBUTES} for testing
    arguments.put("columnOrder", "intAtt,stringAtt,doubleAtt");
    normalMC = buildMetacard();
  }

  private static AttributeDescriptor buildAttributeDescriptor(String name, AttributeType type) {
    return new AttributeDescriptorImpl(name, true, true, true, true, type);
  }

  @Test(expected = CatalogTransformerException.class)
  public void testTransformerWithNullMetacard() throws CatalogTransformerException {
    transformer.transform(nullMC, arguments);
  }

  @Test
  public void testCsvMetacardTransformer() throws CatalogTransformerException, IOException {
    BinaryContent binaryContent = transformer.transform(normalMC, arguments);
    assertThat(binaryContent.getMimeType().toString(), is("text/csv"));

    List<String> attributes = Arrays.asList(new String(binaryContent.getByteArray()).split("\r\n"));
    List<String> csvAttNames = Arrays.asList(attributes.get(0).split(","));
    List<String> csvAttValues = Arrays.asList(attributes.get(1).split(","));

    // Verify columnOrder and aliases are respected
    assertThat(csvAttNames.get(0), is("Int Att"));
    assertThat(csvAttNames.get(1), is("String Att"));
    assertThat(csvAttNames.get(2), is("doubleAtt")); // "doubleAtt" has no alias

    assertThat(csvAttValues.get(0), is("101"));
    assertThat(csvAttValues.get(1), is("stringVal"));
    assertThat(csvAttValues.get(2), is("3.14159"));
  }

  private Metacard buildMetacard() {
    MetacardType metacardType = new MetacardTypeImpl("", ATTRIBUTE_DESCRIPTORS);
    Metacard metacard = new MetacardImpl(metacardType);
    for (Attribute attribute : ATTRIBUTES) {
      metacard.setAttribute(attribute);
    }
    return metacard;
  }

  private AttributeAliases getAliases() {
    return new AttributeAliases() {

      // Intentionally leaving out "doubleAtt"
      ImmutableMap<String, String> aliases =
          ImmutableMap.of("stringAtt", "String Att", "intAtt", "Int Att");

      @Override
      public boolean hasAlias(String attributeName) {
        return aliases.containsKey(attributeName);
      }

      @Override
      public String getAlias(String attributeName) {
        return aliases.get(attributeName);
      }

      @Override
      public ImmutableMap<String, String> getAliasMap() {
        return aliases;
      }
    };
  }
}
