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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.OsgiInstaller;
import org.apache.sling.installer.api.ResourceChangeListener;
import org.apache.sling.installer.api.event.InstallationEvent;
import org.apache.sling.installer.api.event.InstallationListener;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.tasks.ChangeStateTask;
import org.apache.sling.installer.api.tasks.InstallTask;
import org.apache.sling.installer.api.tasks.InstallTaskFactory;
import org.apache.sling.installer.api.tasks.RegisteredResource;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.apache.sling.installer.api.tasks.ResourceTransformer;
import org.apache.sling.installer.api.tasks.TaskResource;
import org.apache.sling.installer.api.tasks.TaskResourceGroup;
import org.apache.sling.installer.api.tasks.TransformationResult;
import org.apache.sling.installer.factories.configuration.ConfigurationConstants;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task creator for configurations.
 */
public class ConfigTaskCreator
    implements InstallTaskFactory, ConfigurationListener, ResourceTransformer, InstallationListener {

    /** Logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Configuration admin. */
    private final ConfigurationAdmin configAdmin;

    /** Resource change listener. */
    private final ResourceChangeListener changeListener;

    /** Resource change listener. */
    private final OsgiInstaller installer;

    /** Resource change listener. */
    private final InfoProvider infoProvider;

    public ConfigTaskCreator(final ResourceChangeListener listener,
            final ConfigurationAdmin configAdmin,
            final OsgiInstaller installer,
            final InfoProvider infoProvider) {
        this.changeListener = listener;
        this.configAdmin = configAdmin;
        this.installer = installer;
        this.infoProvider = infoProvider;
    }

    /**
     * Create a task to install or uninstall a configuration.
     *
	 * @see org.apache.sling.installer.api.tasks.InstallTaskFactory#createTask(org.apache.sling.installer.api.tasks.TaskResourceGroup)
	 */
	@Override
    public InstallTask createTask(final TaskResourceGroup group) {
        final TaskResource toActivate = group.getActiveResource();
        if ( !toActivate.getType().equals(InstallableResource.TYPE_CONFIG) ) {
            return null;
        }

        final InstallTask result;
		if (toActivate.getState() == ResourceState.UNINSTALL) {
            // if this is an uninstall, check if we have to install an older version
            // in this case we should do an update instead of uninstall/install (!)
            final TaskResource second = group.getNextActiveResource();
            if ( second != null
                && ( second.getState() == ResourceState.IGNORED || second.getState() == ResourceState.INSTALLED || second.getState() == ResourceState.INSTALL )
                && ( second.getDictionary() == null || second.getDictionary().get(InstallableResource.RESOURCE_IS_TEMPLATE) == null)) {
                result = new ChangeStateTask(group, ResourceState.UNINSTALLED);
            } else {
                result = new ConfigRemoveTask(group, this.configAdmin);
            }
		} else {
	        result = new ConfigInstallTask(group, this.configAdmin);
		}
		return result;
	}

    /**
     * @see org.osgi.service.cm.ConfigurationListener#configurationEvent(org.osgi.service.cm.ConfigurationEvent)
     */
    @Override
    public void configurationEvent(final ConfigurationEvent event) {
        Configuration updateConfig = null;
        ResourceGroup updateGroup = null;
        synchronized ( Coordinator.SHARED ) {
            if ( event.getType() == ConfigurationEvent.CM_DELETED ) {
                final Coordinator.Operation op = Coordinator.SHARED.get(event.getPid(), event.getFactoryPid(), true);
                if ( op == null ) {
                    updateGroup = this.convertOldInstallerResource(event);
                    if ( updateGroup == null ) {
                        this.changeListener.resourceRemoved(InstallableResource.TYPE_CONFIG, event.getPid());
                    }
                } else {
                    this.logger.debug("Ignoring configuration event for {}:{}", event.getPid(), event.getFactoryPid());
                }
            } else if ( event.getType() == ConfigurationEvent.CM_UPDATED ) {
                try {
                    // we just need to pass in the pid as we're using named factory configs
                    final Configuration config = ConfigUtil.getConfiguration(configAdmin,
                            null,
                            event.getPid());
                    final Coordinator.Operation op = Coordinator.SHARED.get(event.getPid(), event.getFactoryPid(), false);
                    if ( config != null && op == null ) {
                        final boolean persist = ConfigUtil.toBoolean(config.getProperties().get(ConfigurationConstants.PROPERTY_PERSISTENCE), true);

                        final Dictionary<String, Object> dict = ConfigUtil.cleanConfiguration(config.getProperties());
                        final Map<String, Object> attrs = new HashMap<>();
                        if ( !persist ) {
                            attrs.put(ResourceChangeListener.RESOURCE_PERSIST, Boolean.FALSE);
                        }
                        attrs.put(Constants.SERVICE_PID, event.getPid());
                        attrs.put(InstallableResource.RESOURCE_URI_HINT, event.getPid());
                        if ( config.getBundleLocation() != null ) {
                            attrs.put(InstallableResource.INSTALLATION_HINT, config.getBundleLocation());
                        }
                        // Factory?
                        if (event.getFactoryPid() != null) {
                            attrs.put(ConfigurationAdmin.SERVICE_FACTORYPID, event.getFactoryPid());
                        }

                        updateGroup = this.convertOldInstallerResource(event);
                        if ( updateGroup == null ) {
                            this.changeListener.resourceAddedOrUpdated(InstallableResource.TYPE_CONFIG, event.getPid(), null, dict, attrs);
                        } else {
                            updateConfig = config;
                        }

                    } else {
                        this.logger.debug("Ignoring configuration event for {}:{}", event.getPid(), event.getFactoryPid());
                    }
                } catch ( final Exception ignore) {
                    // ignore for now
                }
            }
        }
        if ( updateGroup != null ) {
            // update installer state from old config to new config
            // store config dictionary for update
            final Dictionary<String, Object> dict = updateConfig == null ? null : ConfigUtil.cleanConfiguration(updateConfig.getProperties());
            try {
                String name = null;
                for(final Resource r : updateGroup.getResources()) {
                    if ( name == null ) {
                        name = (String)r.getAttribute(Constants.SERVICE_PID);
                    }
                    final int pos = r.getURL().indexOf(':');
                    final String rsrcPath = r.getURL().substring(pos +1);

                    this.startListener(r.getURL(), 2);
                    final Dictionary<String, Object> newProps = ConfigUtil.cleanConfiguration(r.getDictionary());
                    newProps.put(this.getClass().getName(), "true");
                    final InstallableResource ins1 = new InstallableResource(rsrcPath, null, newProps, r.getDigest(), r.getType(), r.getPriority());
                    this.installer.updateResources(r.getScheme(), new InstallableResource[] {ins1}, null);
                    this.waitForInstall();

                    this.startListener(r.getURL(), 1);
                    newProps.remove(this.getClass().getName());
                    final InstallableResource ins2 = new InstallableResource(rsrcPath, null, newProps, r.getDigest(), r.getType(), r.getPriority());
                    this.installer.updateResources(r.getScheme(), new InstallableResource[] {ins2}, null);
                    this.waitForInstall();
                }
                if ( updateConfig != null ) {
                    // then delete old config
                    final Coordinator.Operation op = new Coordinator.Operation(event.getPid(), event.getFactoryPid(), true);
                    Coordinator.SHARED.add(op);
                    updateConfig.delete();

                    // finally re-create new config
                    final Configuration newConfig = this.configAdmin.getFactoryConfiguration(event.getFactoryPid(), name, null);
                    newConfig.update(dict);
                } else {
                    // delete newly created config
                    final Configuration newConfig = this.configAdmin.getFactoryConfiguration(event.getFactoryPid(), name, null);
                    newConfig.delete();
                }
            } catch ( final Exception ignore) {
                logger.error("An error occured while updating factory configuration " + event.getFactoryPid() + "-" + event.getPid(), ignore);
            }
        }
    }

    private ResourceGroup convertOldInstallerResource(final ConfigurationEvent event) {
        if ( event.getFactoryPid() != null && !event.getPid().contains("~") ) {
            // check if this configuration is processed by the installer
            final String id =  event.getFactoryPid() + '.' + (event.getPid().startsWith(event.getFactoryPid() + '.') ?
                    event.getPid().substring(event.getFactoryPid().length() + 1) : event.getPid());
            final List<ResourceGroup> groups = this.infoProvider.getInstallationState().getInstalledResources();
            for(final ResourceGroup grp : groups) {
                if ( grp.getAlias() != null ) {
                    final String alias;
                    if ( grp.getAlias().startsWith(event.getFactoryPid() + ".") ) {
                        alias = grp.getAlias().substring(event.getFactoryPid().length() + 1);
                    } else {
                        alias = grp.getAlias();
                    }
                    for(final Resource rsrc : grp.getResources()) {
                        if ( InstallableResource.TYPE_CONFIG.equals(rsrc.getType()) && id.equals(alias) ) {
                            // we need to update the installer state
                            return grp;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * @see org.apache.sling.installer.api.tasks.ResourceTransformer#transform(org.apache.sling.installer.api.tasks.RegisteredResource)
     */
    @Override
    public TransformationResult[] transform(final RegisteredResource resource) {
        if ( resource.getType().equals(InstallableResource.TYPE_PROPERTIES) ) {
            return checkConfiguration(resource);
        }
        return null;
    }

    private static String getResourceId(final String rawUrl) {
        final String url = separatorsToUnix(rawUrl);
        int pos = url.lastIndexOf('/');
        if ( pos == -1 ) {
            pos = url.indexOf(':');
        }

        final String lastIdPart;
        if ( pos != -1 ) {
            lastIdPart = url.substring(pos + 1);
        } else {
            lastIdPart = url;
        }
        return lastIdPart;
    }

    /**
     * Check if the registered resource is a configuration
     * @param resource The resource
     */
    private TransformationResult[] checkConfiguration(final RegisteredResource resource) {
        final String lastIdPart = getResourceId(resource.getURL());

        final String pid;
        // remove extension if known
        if ( isConfigExtension(getExtension(lastIdPart)) ) {
            final int lastDot = lastIdPart.lastIndexOf('.');
            pid = lastIdPart.substring(0, lastDot);
        } else {
            pid = lastIdPart;
        }

        // split pid and factory pid alias
        final Map<String, Object> attr = new HashMap<>();
        final String factoryPid;
        final String configPid;
        int n = pid.indexOf('-');
        if (n > 0) {
            configPid = pid.substring(n + 1);
            factoryPid = pid.substring(0, n);
            attr.put(ConfigurationAdmin.SERVICE_FACTORYPID, factoryPid);
        } else {
            factoryPid = null;
            configPid = pid;
        }
        attr.put(Constants.SERVICE_PID, configPid);

        final TransformationResult tr = new TransformationResult();
        final String id = (factoryPid == null ? configPid : ConfigUtil.getPIDOfFactoryPID(factoryPid, configPid));
        tr.setId(id);
        tr.setResourceType(InstallableResource.TYPE_CONFIG);
        tr.setAttributes(attr);

        return new TransformationResult[] {tr};
    }

    private volatile CountDownLatch latch;
    private volatile String waitingForUrl;

    @Override
    public void onEvent(final InstallationEvent event) {
        if ( waitingForUrl != null ) {
            if ( event.getType() == InstallationEvent.TYPE.PROCESSED ) {
                final TaskResource rsrc = (TaskResource) event.getSource();
                if ( rsrc.getURL().equals(waitingForUrl) ) {
                    latch.countDown();
                }
            }
        }
    }


    private void startListener(final String url, final int count) {
        this.latch = new CountDownLatch(count);
        this.waitingForUrl = url;
    }

    public void waitForInstall() {
        try {
            this.latch.await(50, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.waitingForUrl = null;
        this.latch = null;
    }

    /**
     * Compute the extension
     */
    private static String getExtension(final String url) {
        final int pos = url.lastIndexOf('.');
        return (pos < 0 ? "" : url.substring(pos+1));
    }

    private static boolean isConfigExtension(final String extension) {
        if ( extension.equals("cfg")
                || extension.equals("config")
                || extension.equals("xml")
                || extension.equals("properties")) {
            return true;
        }
        return false;
    }

    /**
     * Converts all separators to the Unix separator of forward slash.
     *
     * @param path  the path to be changed, null ignored
     * @return the updated path
     */
    private static String separatorsToUnix(final String path) {
        if (path == null || path.indexOf('\\') == -1) {
            return path;
        }
        return path.replace('\\', '/');
    }
}
