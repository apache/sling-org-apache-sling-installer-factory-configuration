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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sling.installer.api.ResourceChangeListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * The <code>ServicesListener</code> listens for the required services
 * and starts/stops the scanners based on the availability of the
 * services.
 */
public class ServicesListener {

    /** Vendor of all registered services. */
    public static final String VENDOR = "The Apache Software Foundation";

    /** The bundle context. */
    private final BundleContext bundleContext;

    /** The listener for the change list handler. */
    private final Listener changeHandlerListener;

    /** The listener for the configuration admin. */
    private final Listener configAdminListener;

    /** Registration the service. */
    private volatile ServiceRegistration<?> configTaskCreatorRegistration;

    private volatile ConfigTaskCreator configTaskCreator;

    private final AtomicBoolean active = new AtomicBoolean(false);

    public ServicesListener(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.changeHandlerListener = new Listener(ResourceChangeListener.class.getName());
        this.configAdminListener = new Listener(ConfigurationAdmin.class.getName());
        this.changeHandlerListener.start();
        this.configAdminListener.start();
    }

    public synchronized void notifyChange() {
        // check if all services are available
        final ResourceChangeListener listener = (ResourceChangeListener)this.changeHandlerListener.getService();
        final ConfigurationAdmin configAdmin = (ConfigurationAdmin)this.configAdminListener.getService();

        if ( configAdmin != null && listener != null ) {
            if ( configTaskCreator == null ) {
                active.set(true);
                // start and register osgi installer service
                this.configTaskCreator = new ConfigTaskCreator(listener, configAdmin);
                final ConfigUpdateHandler handler = new ConfigUpdateHandler(configAdmin, this);
                configTaskCreatorRegistration = handler.register(this.bundleContext);
            }
        } else {
            this.stop();
        }
    }

    private synchronized void stop() {
        active.set(false);
        // unregister
        if ( this.configTaskCreatorRegistration != null ) {
            this.configTaskCreatorRegistration.unregister();
            this.configTaskCreatorRegistration = null;
        }
        this.configTaskCreator = null;
    }

    public boolean isActive() {
        return this.active.get();
    }

    public synchronized void finishedUpdating() {
        if ( this.isActive() ) {
            this.configTaskCreatorRegistration.unregister();
            this.configTaskCreatorRegistration = this.configTaskCreator.register(this.bundleContext);
        }
    }

    /**
     * Deactivate this listener.
     */
    public void deactivate() {
        this.changeHandlerListener.deactivate();
        this.configAdminListener.deactivate();
        this.stop();
    }

    protected final class Listener implements ServiceListener {

        private final String serviceName;

        private ServiceReference<?> reference;
        private Object service;

        public Listener(final String serviceName) {
            this.serviceName = serviceName;
        }

        public void start() {
            this.retainService();
            try {
                bundleContext.addServiceListener(this, "("
                        + Constants.OBJECTCLASS + "=" + serviceName + ")");
            } catch (final InvalidSyntaxException ise) {
                // this should really never happen
                throw new RuntimeException("Unexpected exception occured.", ise);
            }
        }

        public void deactivate() {
            bundleContext.removeServiceListener(this);
        }

        public synchronized Object getService() {
            return this.service;
        }
        private synchronized void retainService() {
            if ( this.reference == null ) {
                this.reference = bundleContext.getServiceReference(this.serviceName);
                if ( this.reference != null ) {
                    this.service = bundleContext.getService(this.reference);
                    if ( this.service == null ) {
                        this.reference = null;
                    } else {
                        notifyChange();
                    }
                }
            }
        }

        private synchronized void releaseService() {
            if ( this.reference != null ) {
                this.service = null;
                bundleContext.ungetService(this.reference);
                this.reference = null;
                notifyChange();
            }
        }

        /**
         * @see org.osgi.framework.ServiceListener#serviceChanged(org.osgi.framework.ServiceEvent)
         */
        @Override
        public void serviceChanged(ServiceEvent event) {
            if (event.getType() == ServiceEvent.REGISTERED ) {
                this.retainService();
            } else if ( event.getType() == ServiceEvent.UNREGISTERING ) {
                this.releaseService();
            }
        }
    }
}
