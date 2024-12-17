/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import static com.hedera.node.app.info.UnavailableNetworkInfo.UNAVAILABLE_NETWORK_INFO;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UnavailableNetworkInfoTest {
    @Test
    void noMethodIsSupported() {
        assertThrows(UnsupportedOperationException.class, UNAVAILABLE_NETWORK_INFO::ledgerId);
        assertThrows(UnsupportedOperationException.class, UNAVAILABLE_NETWORK_INFO::selfNodeInfo);
        assertThrows(UnsupportedOperationException.class, UNAVAILABLE_NETWORK_INFO::addressBook);
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_NETWORK_INFO.nodeInfo(0));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_NETWORK_INFO.containsNode(0));
    }
}
