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

import java.util.Arrays;
import java.util.List;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

/**
 * The activator registers the configuration support service.
 */
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    /** Property for bundle location default. */
    private static final String PROP_LOCATION_DEFAULT = "sling.installer.config.useMulti";

    /** Property for configuration merge schemes. */
    private static final String PROP_MERGE_SCHEMES = "sling.installer.config.mergeSchemes";

    /** Services listener. */
    private ServicesListener listener;

    public static String DEFAULT_LOCATION;

    public static List<String> MERGE_SCHEMES;

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start(final BundleContext context) throws Exception {
        if (context.getProperty(PROP_LOCATION_DEFAULT) != null) {
            final Boolean bool =
                    Boolean.valueOf(context.getProperty(PROP_LOCATION_DEFAULT).toString());
            if (bool.booleanValue()) {
                DEFAULT_LOCATION = "?";
            }
        }
        if (context.getProperty(PROP_MERGE_SCHEMES) != null) {
            MERGE_SCHEMES =
                    Arrays.asList(context.getProperty(PROP_MERGE_SCHEMES).split(","));
        }
        this.listener = new ServicesListener(context);
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(final BundleContext context) {
        if (this.listener != null) {
            this.listener.deactivate();
            this.listener = null;
        }
    }
}
