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

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.serializer.ConfigurationSerializerFactory;
import org.apache.sling.installer.api.serializer.ConfigurationSerializerFactory.Format;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = javax.servlet.Servlet.class,
        property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            Constants.SERVICE_DESCRIPTION + "=Apache Sling OSGi Installer Configuration Serializer Web Console Plugin",
            "felix.webconsole.label=" + ConfigurationSerializerWebConsolePlugin.LABEL,
            "felix.webconsole.title=OSGi Installer Configuration Printer",
            "felix.webconsole.category=OSGi"
        })
@SuppressWarnings("serial")
public class ConfigurationSerializerWebConsolePlugin extends GenericServlet {

    public static final String LABEL = "osgi-installer-config-printer";
    private static final String RES_LOC = LABEL + "/res/ui/";
    private static final String PARAMETER_PID = "pid";
    private static final String PARAMETER_FORMAT = "format";
    private static final String PARAMETER_HIDE_REDUNDANT_PROPERTIES = "hideRedundantProperties";

    /** The logger */
    private final Logger LOGGER = LoggerFactory.getLogger(ConfigurationSerializerWebConsolePlugin.class);

    @Reference
    ConfigurationAdmin configurationAdmin;

    @Reference
    private InfoProvider infoProvider;

    @Reference
    private ServiceComponentRuntime scr;

    @Override
    public void service(final ServletRequest request, final ServletResponse response) throws IOException {
        final String pid = request.getParameter(PARAMETER_PID);
        final Configuration configuration;
        if (pid != null && !pid.trim().isEmpty()) {
            configuration = configurationAdmin.getConfiguration(pid, null);
        } else {
            configuration = null;
        }
        final String format = request.getParameter(PARAMETER_FORMAT);
        ConfigurationSerializerFactory.Format serializationFormat = Format.JSON;
        if (format != null && !format.trim().isEmpty()) {
            try {
                serializationFormat = ConfigurationSerializerFactory.Format.valueOf(format);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Illegal parameter 'format' given, falling back to default '{}'", serializationFormat, e);
            }
        }
        final boolean hideRedundantProperties;
        if (format == null) {
            hideRedundantProperties = true;
        } else {
            hideRedundantProperties = Boolean.parseBoolean(request.getParameter(PARAMETER_HIDE_REDUNDANT_PROPERTIES));
        }
        dumpConfiguration(configuration, serializationFormat, hideRedundantProperties, response.getWriter());
    }

    private void dumpConfiguration(
            Configuration configuration,
            ConfigurationSerializerFactory.Format serializationFormat,
            boolean hideRedundantProperties,
            PrintWriter pw) {
        // map with key = configuration pid and value = Set<ComponentDescriptionDTO>
        Map<String, Set<ComponentDescriptionDTO>> allComponentDescriptions = new HashMap<>();
        String pid = configuration != null ? configuration.getPid() : "";
        scr.getComponentDescriptionDTOs().stream().forEach(dto -> {
            for (String configPid : dto.configurationPid) {
                // the same PID might be bound to multiple component descriptions
                allComponentDescriptions
                        .computeIfAbsent(configPid, k -> new HashSet<ComponentDescriptionDTO>())
                        .add(dto);
            }
        });

        pw.println("<script type=\"text/javascript\" src=\"" + RES_LOC + "clipboard.js\"></script>");
        pw.print("<form method='get'>");
        pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");

        titleHtml(
                pw,
                "OSGi Installer Configuration Printer",
                "To emit the configuration properties just enter the configuration PID, select a <a href='https://sling.apache.org/documentation/bundles/configuration-installer-factory.html'>serialization format</a> and click 'Print'");

        tr(pw);
        tdLabel(pw, "PID");
        tdContent(pw);

        pw.printf(
                "<input type='text' name='%s' ' value='%s' class='input' size='120' minlength='3'>",
                PARAMETER_PID, escapeXml(pid));
        pw.println();
        pw.println(
                "<p>For factory configurations use the factory PID followed by a tilde and the configuration name, e.g. 'my.factory.pid~myname'</p>");
        closeTd(pw);
        closeTr(pw);

        tr(pw);

        tdLabel(pw, "Hide Properties");
        tdContent(pw);

        pw.append("<input type='checkbox' name='");
        pw.append(PARAMETER_HIDE_REDUNDANT_PROPERTIES);
        pw.append("'");
        if (hideRedundantProperties) {
            pw.print(" checked");
        }

        pw.append(" id='");
        pw.append(PARAMETER_HIDE_REDUNDANT_PROPERTIES);
        pw.append("' class='input' value='true'>").println();
        pw.append("<label for='");
        pw.append(PARAMETER_HIDE_REDUNDANT_PROPERTIES);
        pw.append("'>");
        pw.append("Redundant Properties (");
        StringBuilder sb = new StringBuilder();
        // these configs come from inherited sources
        Dictionary<String, Object> mergedProperties = ConfigTaskCreator.getDefaultProperties(infoProvider, pid);
        if (mergedProperties == null) {
            mergedProperties = new Hashtable<>();
        }
        if (mergedProperties.size() > 0) {
            sb.append(
                    "from <a href=\"https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#merging-of-configurations\">Merge Schemes</a> ");
            sb.append("\"").append(String.join(", ", Activator.MERGE_SCHEMES)).append("\"");
        }
        final String pidReferencedFromComponentDescription;
        if (configuration != null) {
            pidReferencedFromComponentDescription =
                    configuration.getFactoryPid() != null ? configuration.getFactoryPid() : configuration.getPid();
            Set<ComponentDescriptionDTO> componentDescriptions =
                    allComponentDescriptions.get(pidReferencedFromComponentDescription);
            if (componentDescriptions != null) {
                if (sb.length() > 0) {
                    sb.append(" and ");
                }
                sb.append("from component description(s) of ");
                sb.append(componentDescriptions.stream()
                        .map(componentDescription -> String.format(
                                "<a href=\"components/%d/%s/%s\">component \"%s\" (bundle %d)</a>",
                                componentDescription.bundle.id,
                                componentDescription.name,
                                componentDescription.configurationPid[0],
                                componentDescription.name,
                                componentDescription.bundle.id))
                        .collect(Collectors.joining(", ")));
            }
        } else {
            pidReferencedFromComponentDescription = "";
        }
        if (sb.length() == 0) {
            sb.append("no fallback sources found");
        }
        pw.append(sb.toString()).append(")</label>").println();
        pw.println(
                "<p>Enabling it hides those properties which have the same name and value as any of their fallback values.</p>");
        closeTd(pw);
        closeTr(pw);

        tr(pw);
        tdLabel(pw, "Serialization Format");
        tdContent(pw);
        pw.print("<select name='");
        pw.print(PARAMETER_FORMAT);
        pw.println("'>");
        option(pw, "JSON", "OSGi Configurator JSON", serializationFormat.name());
        option(pw, "CONFIG", "Apache Felix Config", serializationFormat.name());
        option(pw, "PROPERTIES", "Java Properties", serializationFormat.name());
        option(pw, "PROPERTIES_XML", "Java Properties (XML)", serializationFormat.name());
        pw.println("</select>");

        pw.println("&nbsp;&nbsp;<input type='submit' value='Print' class='submit'>");

        closeTd(pw);
        closeTr(pw);

        if (configuration != null) {
            tr(pw);
            tdLabel(pw, "Serialized Configuration Properties");
            tdContent(pw);

            Dictionary<String, Object> properties = configuration.getProperties();
            if (properties == null) {
                pw.print("<p class='ui-state-error-text'>");
                pw.print("No configuration properties for pid '" + escapeXml(pid) + "' found!");
                pw.println("</p>");
            } else {
                properties = ConfigUtil.cleanConfiguration(properties);
                if (hideRedundantProperties) {
                    removeComponentDefaultProperties(
                            allComponentDescriptions,
                            pidReferencedFromComponentDescription,
                            properties,
                            mergedProperties);
                    ConfigUtil.removeRedundantProperties(properties, mergedProperties);
                }

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    // always emit in alphabetical order of keys
                    ConfigurationSerializerFactory.create(serializationFormat)
                            .serialize(new SortedDictionary<>(properties), baos);
                    pw.println("<textarea rows=\"20\" cols=\"120\" id=\"output\" readonly>");
                    pw.print(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                    pw.println("</textarea>");
                    pw.println("<button type='button' id='copy'>Copy to Clipboard</a>");
                } catch (Exception e) {
                    pw.print("<p class='ui-state-error-text'>");
                    pw.print("Error serializing pid '" + escapeXml(pid) + "': " + e.getMessage());
                    pw.println("</p>");
                    LOGGER.warn("Error serializing pid '{}'", pid, e);
                }
            }
            closeTd(pw);
            closeTr(pw);
        }
        pw.println("</table>");
        pw.print("</form>");
    }

    private void tdContent(final PrintWriter pw) {
        pw.print("<td class='content' colspan='2'>");
    }

    private void closeTd(final PrintWriter pw) {
        pw.print("</td>");
    }

    private void closeTr(final PrintWriter pw) {
        pw.println("</tr>");
    }

    private void tdLabel(final PrintWriter pw, final String label) {
        pw.print("<td class='content'>");
        pw.print(label);
        pw.println("</td>");
    }

    private void tr(final PrintWriter pw) {
        pw.println("<tr class='content'>");
    }

    private void option(final PrintWriter pw, String value, String label, String selectedValue) {
        pw.print("<option value='");
        pw.print(value);
        pw.print("'");
        if (value.equals(selectedValue)) {
            pw.print(" selected");
        }
        pw.print(">");
        pw.print(label);
        pw.println("</option>");
    }

    private void titleHtml(final PrintWriter pw, final String title, final String description) {
        tr(pw);
        pw.print("<th colspan='3' class='content container'>");
        pw.print(escapeXml(title));
        pw.println("</th>");
        closeTr(pw);

        if (description != null) {
            tr(pw);
            pw.print("<td colspan='3' class='content'>");
            pw.print(description);
            pw.println("</th>");
            closeTr(pw);
        }
    }

    /**
     * Copied from org.apache.sling.api.request.ResponseUtil
     * Escape XML text
     * @param input The input text
     * @return The escaped text
     */
    protected String escapeXml(final String input) {
        if (input == null) {
            return null;
        }

        final StringBuilder b = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c == '&') {
                b.append("&amp;");
            } else if (c == '<') {
                b.append("&lt;");
            } else if (c == '>') {
                b.append("&gt;");
            } else if (c == '"') {
                b.append("&quot;");
            } else if (c == '\'') {
                b.append("&apos;");
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Removes all configuration properties from the given dictionary whose values are equal to all connected DS component properties set in the component description
     * and which are not set in the merged (i.e. inherited) properties.
     * @param allComponentDescriptions
     * @param pidReferencedFromComponentDescription The PID referenced in the component description
     * @param properties all properties of the configuration (is potentially modified through this method)
     * @param mergedProperties the merged/inherited properties from some other OSGi installer resource
     */
    private void removeComponentDefaultProperties(
            final Map<String, Set<ComponentDescriptionDTO>> allComponentDescriptions,
            final String pidReferencedFromComponentDescription,
            final Dictionary<String, Object> properties,
            final Dictionary<String, Object> mergedProperties) {
        Set<ComponentDescriptionDTO> componentDescriptions =
                allComponentDescriptions.get(pidReferencedFromComponentDescription);

        final Enumeration<String> e = properties.keys();
        while (e.hasMoreElements()) {
            final String key = e.nextElement();
            final Object newValue = properties.get(key);
            if (componentDescriptions != null) {
                // check all bound component descriptions
                Set<Object> defaultValues = componentDescriptions.stream()
                        .map(dto -> dto.properties.get(key))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (defaultValues.size() != 1) {
                    // if different components have different values for the same key, we cannot remove it
                    continue;
                }
                Object defaultValue = defaultValues.iterator().next();
                if (ConfigUtil.isSameValue(newValue, defaultValue) && mergedProperties.get(key) == null) {
                    properties.remove(key);
                }
            }
        }
    }

    String getRelativeResourcePrefix() {
        return RES_LOC;
    }

    /**
     * Method to retrieve static resources from this bundle.
     */
    @SuppressWarnings("unused")
    private URL getResource(final String path) {
        if (path.startsWith("/" + getRelativeResourcePrefix())) {
            // strip label
            int index = path.indexOf('/', 1);
            if (index <= 0) {
                throw new IllegalStateException("The relativeResourcePrefix must contain at least one '/'");
            }
            return this.getClass().getResource(path.substring(index));
        }
        return null;
    }
}
