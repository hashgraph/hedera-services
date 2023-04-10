/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class NetworkTests {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Validates that the local ip is retrieved as internal ip")
    public void getInternalIPAddressTest() {
        final String internalIp = Network.getInternalIPAddress();
        assertFalse(internalIp.isEmpty());
        assertNotEquals("127.0.0.1", internalIp);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("No ip is found running as unit test")
    public void getExternalIpAddressWithNoIpFound() {
        final ExternalIpAddress address = Network.getExternalIpAddress();
        assertEquals(ExternalIpAddress.NO_IP, address, "No IP should be found on unit test");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("No IP is found with empty collection of ports to be mapped")
    public void getNoIpFoundWithNoPorts() {
        Network.doPortForwarding(getStaticThreadManager(), Collections.emptyList());
        final ExternalIpAddress address = Network.getExternalIpAddress();
        assertEquals(IpAddressStatus.NO_IP_FOUND, address.getStatus(), "No IP should be found on unit test");
        Network.stopPortForwarding();
    }
}
