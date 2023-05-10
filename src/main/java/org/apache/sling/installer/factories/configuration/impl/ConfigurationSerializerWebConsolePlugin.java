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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.stream.Collectors;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.serializer.ConfigurationSerializerFactory;
import org.apache.sling.installer.api.serializer.ConfigurationSerializerFactory.Format;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.metatype.MetaTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service=javax.servlet.Servlet.class,
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
    private static final String PARAMETER_REMOVE_COMPONENT_DEFAULT_PROPERTIES = "removeComponentDefaultProps";
    private static final String PARAMETER_REMOVE_METATYPE_DEFAULT_PROPERTIES = "removeMetatypeDefaultProps";
    private static final String PARAMETER_REMOVE_MERGED_DEFAULT_PROPERTIES = "removeMergedDefaultProps";

    /** The logger */
    private final Logger LOGGER =  LoggerFactory.getLogger(ConfigurationSerializerWebConsolePlugin.class);

    @Reference
    ConfigurationAdmin configurationAdmin;

    @Reference
    MetaTypeService metatypeService;

    @Reference
    private InfoProvider infoProvider;

    @Reference
    private ServiceComponentRuntime scr;

    private final BundleContext bundleContext;

    @Activate
    public ConfigurationSerializerWebConsolePlugin(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public void service(final ServletRequest request, final ServletResponse response)
            throws IOException {
        
        final String pid = request.getParameter(PARAMETER_PID);
        final String format = request.getParameter(PARAMETER_FORMAT);
        // initial loading
        final boolean removeMetatypeDefaultProperties;
        final boolean removeComponentDefaultProperties;
        final boolean removeMergedDefaultProperties;
        // initial loading?
        if (format == null) {
            removeMetatypeDefaultProperties = true;
            removeComponentDefaultProperties = true;
            removeMergedDefaultProperties = true;
        } else {
            removeMetatypeDefaultProperties = Boolean.parseBoolean(request.getParameter(PARAMETER_REMOVE_METATYPE_DEFAULT_PROPERTIES));
            removeComponentDefaultProperties = Boolean.parseBoolean(request.getParameter(PARAMETER_REMOVE_COMPONENT_DEFAULT_PROPERTIES));
            removeMergedDefaultProperties = Boolean.parseBoolean(request.getParameter(PARAMETER_REMOVE_MERGED_DEFAULT_PROPERTIES));
        }
        Collection<ComponentDescriptionDTO> allComponentDescriptions;
        if (removeComponentDefaultProperties) {
            allComponentDescriptions = scr.getComponentDescriptionDTOs();
        } else {
            allComponentDescriptions = Collections.emptyList();
        }
        
        MetatypeHandler metatypeHandler = new MetatypeHandler(metatypeService, bundleContext);
        Dictionary<String, Object> mergedProperties = ConfigTaskCreator.getDefaultProperties(infoProvider, pid);
        if (mergedProperties == null) {
            mergedProperties = new Hashtable<>();
        }
        ConfigurationSerializerFactory.Format serializationFormat = Format.JSON;
        if (format != null && !format.trim().isEmpty()) {
            try {
                serializationFormat = ConfigurationSerializerFactory.Format.valueOf(format);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Illegal parameter 'format' given, falling back to default '{}'", serializationFormat, e);
            }
        }
        final PrintWriter pw = response.getWriter();

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

        pw.print("<input type='text' name='");
        pw.print(PARAMETER_PID);
        pw.print("' value='");
        if ( pid != null ) {
            pw.print(escapeXml(pid));
        }
        
        pw.println("' class='input' size='120' minlength='3'>");
        closeTd(pw);
        closeTr(pw);

        tr(pw);
        tdLabel(pw, "Remove Properties");
        tdContent(pw);

        pw.print("<input type='checkbox' name='");
        pw.print(PARAMETER_REMOVE_METATYPE_DEFAULT_PROPERTIES);
        pw.print("'");
        if ( removeMetatypeDefaultProperties ) {
            pw.print(" checked");
        }
        
        pw.println(" id='");
        pw.print(PARAMETER_REMOVE_METATYPE_DEFAULT_PROPERTIES);
        pw.println("' class='input' value='true'>");
        pw.println("<label for='");
        pw.println(PARAMETER_REMOVE_METATYPE_DEFAULT_PROPERTIES);
        pw.println("'>Metatype Default Properties</label>");

        pw.print("<input type='checkbox' name='");
        pw.print(PARAMETER_REMOVE_COMPONENT_DEFAULT_PROPERTIES);
        pw.print("'");
        if ( removeComponentDefaultProperties ) {
            pw.print(" checked");
        }

        pw.println(" id='");
        pw.print(PARAMETER_REMOVE_COMPONENT_DEFAULT_PROPERTIES);
        pw.println("' class='input' value='true'>");
        pw.println("<label for='");
        pw.println(PARAMETER_REMOVE_COMPONENT_DEFAULT_PROPERTIES);
        pw.println("'>Declarative Services Component Properties</label>");

        if (Activator.MERGE_SCHEMES != null) {
            pw.print("<input type='checkbox' name='");
            pw.print(PARAMETER_REMOVE_MERGED_DEFAULT_PROPERTIES);
            pw.print("'");
            if ( removeMergedDefaultProperties ) {
                pw.print(" checked");
            }
            
            pw.println(" id='");
            pw.print(PARAMETER_REMOVE_MERGED_DEFAULT_PROPERTIES);
            pw.println("' class='input' value='true'>");
            pw.println("<label for='");
            pw.println(PARAMETER_REMOVE_MERGED_DEFAULT_PROPERTIES);
            pw.println("'>Merged Properties</label>");
        }
        pw.println("<p>Selecting any of these options strips those properties which have the same name and value as one from any of the selected sources. The removed properties are very likely being redundant and therefore do not need to be added to serialized configs.</a>");
        closeTd(pw);
        closeTr(pw);

        tr(pw);
        tdLabel(pw, "Serialization Format");
        tdContent(pw);
        pw.print("<select name='");
        pw.print(PARAMETER_FORMAT);
        pw.println("'>");
        option(pw, "JSON", "OSGi Configurator JSON", format);
        option(pw, "CONFIG", "Apache Felix Config", format);
        option(pw, "PROPERTIES", "Java Properties", format);
        option(pw, "PROPERTIES_XML", "Java Properties (XML)", format);
        pw.println("</select>");

        pw.println("&nbsp;&nbsp;<input type='submit' value='Print' class='submit'>");

        closeTd(pw);
        closeTr(pw);

        if (pid != null && !pid.trim().isEmpty()) {
            tr(pw);
            tdLabel(pw, "Serialized Configuration Properties");
            tdContent(pw);
            
            Configuration configuration = configurationAdmin.getConfiguration(pid, null);
            Dictionary<String, Object> properties = configuration.getProperties();
            if (properties == null) {
                pw.print("<p class='ui-state-error-text'>");
                pw.print("No configuration properties for pid '" + escapeXml(pid) + "' found!");
                pw.println("</p>");
            } else {
                properties = ConfigUtil.cleanConfiguration(properties);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    if (removeMetatypeDefaultProperties) {
                        metatypeHandler.updateConfiguration(configuration.getFactoryPid(), configuration.getPid(), properties, mergedProperties);
                    }
                    if (removeComponentDefaultProperties) {
                        removeComponentDefaultProperties(allComponentDescriptions, configuration.getPid(), configuration.getFactoryPid(), properties, mergedProperties);
                    }
                    if (removeMergedDefaultProperties) {
                        ConfigUtil.removeRedundantProperties(properties, mergedProperties);
                    }
                    ConfigurationSerializerFactory.create(serializationFormat).serialize(properties, baos);
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
        for(int i = 0;i  < input.length(); i++) {
            final char c = input.charAt(i);
            if(c == '&') {
                b.append("&amp;");
            } else if(c == '<') {
                b.append("&lt;");
            } else if(c == '>') {
                b.append("&gt;");
            } else if(c == '"') {
                b.append("&quot;");
            } else if(c == '\'') {
                b.append("&apos;");
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Removes all configuration properties from the given dictionary whose values are equal to all connected DS component properties set in the descriptor
     * and which are not set in the merged (i.e. inherited) properties.
     * @param componentDescriptions
     * @param pid
     * @param factoryPid
     * @param dict
     * @param mergedProperties
     */
    private void removeComponentDefaultProperties(final Collection<ComponentDescriptionDTO> componentDescriptions, final String pid, final String factoryPid, final Dictionary<String, Object> dict, final Dictionary<String, Object> mergedProperties) {
        String effectivePid = factoryPid != null ? factoryPid : pid;
        Collection<ComponentDescriptionDTO> relevantComponentDescriptions = componentDescriptions.stream()
            // find all with a matching pid
            .filter(c -> Arrays.asList(c.configurationPid).contains(effectivePid)).collect(Collectors.toList());

        final Enumeration<String> e = dict.keys();
        while(e.hasMoreElements()) {
            final String key = e.nextElement();
            final Object newValue = dict.get(key);
            if (relevantComponentDescriptions.stream()
                    .allMatch(c -> ConfigUtil.isSameValue(newValue, c.properties.get(key)) 
                                   && mergedProperties.get(key) == null)) {
                dict.remove(key);
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
