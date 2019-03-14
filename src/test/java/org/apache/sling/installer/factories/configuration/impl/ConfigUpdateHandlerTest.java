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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConfigUpdateHandlerTest {

    private void checkFactoryPid(final String alias, final String oldId, final String factoryId, final String pid) {
        final ConfigUpdateHandler cuh = new ConfigUpdateHandler(null, null);
        final String[] result = cuh.getFactoryPidAndPid(alias, oldId);
        assertEquals(factoryId, result[0]);
        assertEquals(pid, result[1]);
    }

    @Test public void testGettingFactoryPid() {
        // normal conversion
        checkFactoryPid("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment.org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment.43e4778d-3e72-460a-9da9-bca80558f1f7",
                "org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment.my-platform",
                "org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment", "my-platform");
        // case where the pid starts with the same characters as the factory pid : "c"
        checkFactoryPid(
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.08f330fd-63d2-4175-ad3c-79efa3c69e2f",
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.cloud",
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup", "cloud");
        // case where the pid starts with the same characters as the factory pid : "co"
        checkFactoryPid(
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.3ba307f5-a5d0-40a4-98b6-8616b7a1d1e8",
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup.contentpackages",
                "com.apache.sling.upgrades.cleanup.impl.UpgradeContentCleanup", "contentpackages");
    }
}
