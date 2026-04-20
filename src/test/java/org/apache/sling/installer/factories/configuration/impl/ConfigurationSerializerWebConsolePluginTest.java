/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.installer.factories.configuration.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.InstallationState;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.serializer.ConfigurationSerializerFactory;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.osgi.framework.dto.BundleDTO;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 *
 */
@ExtendWith(SlingContextExtension.class)
class ConfigurationSerializerWebConsolePluginTest {

    public final SlingContext context = new SlingContext();

    private ConfigurationSerializerWebConsolePlugin plugin;

    private InfoProvider mockInfoProvider;
    private ServiceComponentRuntime mockScr;

    @BeforeEach
    void beforeEach() {
        mockInfoProvider = context.registerService(InfoProvider.class, Mockito.mock(InfoProvider.class));
        mockScr = context.registerService(ServiceComponentRuntime.class, Mockito.mock(ServiceComponentRuntime.class));

        plugin = context.registerInjectActivateService(ConfigurationSerializerWebConsolePlugin.class);
    }

    protected static Stream<Arguments> testServiceArgs() {
        return Stream.of(
                Arguments.of(Map.of()),
                Arguments.of(Map.of("pid", "")),
                Arguments.of(Map.of("pid", "test1")),
                Arguments.of(Map.of("pid", "test1", "format", "")),
                Arguments.of(Map.of("pid", "test1", "format", "JSON")),
                Arguments.of(Map.of("pid", "test2", "format", "JSON")),
                Arguments.of(Map.of("pid", "test1", "format", "INVALID")));
    }

    /**
     * Test method for {@link org.apache.sling.installer.factories.configuration.impl.ConfigurationSerializerWebConsolePlugin#service(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse)}.
     */
    @ParameterizedTest
    @MethodSource("testServiceArgs")
    void testService(Map<String, Object> reqParams) throws IOException {
        // mock InfoProvider
        mockInstallationState();

        // mock existing test1 component configuration
        mockExistingConfiguration("test1", Map.of("key1", "value1"));

        // mock DTO for test1 component
        mockComponentDescriptionDTO("test1", Map.of());

        final @NotNull MockSlingJakartaHttpServletRequest req = context.jakartaRequest();
        req.setParameterMap(reqParams);
        final @NotNull MockSlingJakartaHttpServletResponse resp = context.jakartaResponse();
        plugin.service(req, resp);
        final String outputAsString = resp.getOutputAsString();
        assertNotNull(outputAsString);
    }

    @Test
    void testServiceWithSerializerException() throws Exception {
        // mock existing test1 component configuration
        mockExistingConfiguration("test1", Map.of("key1", "value1"));

        final @NotNull MockSlingJakartaHttpServletRequest req = context.jakartaRequest();
        req.setParameterMap(Map.of("pid", "test1"));
        final @NotNull MockSlingJakartaHttpServletResponse resp = context.jakartaResponse();
        try (MockedStatic<ConfigurationSerializerFactory> factoryMock =
                Mockito.mockStatic(ConfigurationSerializerFactory.class); ) {
            factoryMock
                    .when(() -> ConfigurationSerializerFactory.create(any(ConfigurationSerializerFactory.Format.class)))
                    .thenThrow(UnsupportedOperationException.class);

            plugin.service(req, resp);
            final String outputAsString = resp.getOutputAsString();
            assertNotNull(outputAsString);
            assertTrue(outputAsString.contains("Error serializing pid"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testServiceWithMergeScheme(boolean createDTO) throws IOException {
        Activator.MERGE_SCHEMES = List.of("launchpad");

        // mock InfoProvider
        final InstallationState mockInstallationState = mockInstallationState();
        ResourceGroup mockResourceGroup = Mockito.mock(ResourceGroup.class);
        Resource mockInstalledResource = Mockito.mock(Resource.class);
        Mockito.doReturn("config:factory1~test1").when(mockInstalledResource).getEntityId();
        Mockito.doReturn("launchpad").when(mockInstalledResource).getScheme();
        Mockito.doReturn(new Hashtable<>(Map.of("key1", "value1", "key2", "value2")))
                .when(mockInstalledResource)
                .getDictionary();
        Mockito.doReturn(List.of(mockInstalledResource)).when(mockResourceGroup).getResources();
        Mockito.doReturn(List.of(mockResourceGroup)).when(mockInstallationState).getInstalledResources();

        // mock existing test1 component configuration
        mockExistingFactoryConfiguration(
                "factory1",
                "test1",
                Map.of("key1", "value1", "key2", "value2changed", "key3", "value3", "key4", "value4"));

        if (createDTO) {
            // mock DTO for test1 component
            mockComponentDescriptionDTO(
                    "factory1", Map.of("key1", "value1", "key3", "value3", "key4", "value4changed"));
        }

        final @NotNull MockSlingJakartaHttpServletRequest req = context.jakartaRequest();
        req.setParameterMap(Map.of("pid", "factory1~test1", "hideRedundantProperties", "true"));
        final @NotNull MockSlingJakartaHttpServletResponse resp = context.jakartaResponse();
        plugin.service(req, resp);
        final String outputAsString = resp.getOutputAsString();
        assertNotNull(outputAsString);
    }

    private void mockComponentDescriptionDTO(String configPid, Map<String, Object> properties) {
        ComponentDescriptionDTO mockDTO = Mockito.mock(ComponentDescriptionDTO.class);
        mockDTO.bundle = Mockito.mock(BundleDTO.class);
        mockDTO.bundle.id = 1;
        mockDTO.name = "name1";
        mockDTO.configurationPid = new String[] {configPid};
        mockDTO.properties = properties;
        Mockito.doReturn(List.of(mockDTO)).when(mockScr).getComponentDescriptionDTOs();
    }

    private void mockExistingConfiguration(String pid, Map<String, Object> props) throws IOException {
        ConfigurationAdmin configAdmin = context.getService(ConfigurationAdmin.class);
        final Configuration test1Configuration = configAdmin.getConfiguration(pid);
        test1Configuration.update(new Hashtable<>(props));
    }

    private void mockExistingFactoryConfiguration(String factoryPid, String name, Map<String, Object> props)
            throws IOException {
        ConfigurationAdmin configAdmin = context.getService(ConfigurationAdmin.class);
        final Configuration test1Configuration = configAdmin.getFactoryConfiguration(factoryPid, name);
        test1Configuration.update(new Hashtable<>(props));
    }

    private InstallationState mockInstallationState() {
        InstallationState mockInstallationState = Mockito.mock(InstallationState.class);
        Mockito.doReturn(mockInstallationState).when(mockInfoProvider).getInstallationState();
        return mockInstallationState;
    }

    /**
     * Test method for {@link org.apache.sling.installer.factories.configuration.impl.ConfigurationSerializerWebConsolePlugin#escapeXml(java.lang.String)}.
     */
    @Test
    void testEscapeXml() {
        assertNull(plugin.escapeXml(null));
        assertEquals("", plugin.escapeXml(""));
        assertEquals("&lt;hello/&gt;", plugin.escapeXml("<hello/>"));
        assertEquals("&quot;dog&apos;s &amp; cat&quot;", plugin.escapeXml("\"dog's & cat\""));
    }

    /**
     * Test method for {@link org.apache.sling.installer.factories.configuration.impl.ConfigurationSerializerWebConsolePlugin#getRelativeResourcePrefix()}.
     */
    @Test
    void testGetRelativeResourcePrefix() {
        assertNotNull(plugin.getRelativeResourcePrefix());
    }

    /**
     * Test method for {@link
     * org.apache.sling.installer.factories.configuration.impl.ConfigurationSerializerWebConsolePlugin#getResource(java.lang.String)}.
     */
    @Test
    void testGetResource() throws SecurityException, IllegalArgumentException {
        final URL value1 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {"/invalid"});
        assertNull(value1);

        final URL value2 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {
                    "/osgi-installer-config-printer/res/ui/clipboard.js"
                });
        assertNotNull(value2);

        final URL value3 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {
                    "/osgi-installer-config-printer/res/ui/invalid.css"
                });
        assertNull(value3);
    }
}
