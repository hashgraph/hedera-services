/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.p2p.portforwarding;

import com.swirlds.p2p.portforwarding.PortForwarder.Protocol;

public class PortMapping {
    private final String ip;
    private final int internalPort;
    private final int externalPort;
    private final Protocol protocol;

    public PortMapping(final String ip, final int internalPort, final int externalPort, Protocol protocol) {
        this.ip = ip;
        this.internalPort = internalPort;
        this.externalPort = externalPort;
        this.protocol = protocol;
    }

    public String getIp() {
        return ip;
    }

    public int getInternalPort() {
        return internalPort;
    }

    public int getExternalPort() {
        return externalPort;
    }

    public Protocol getProtocol() {
        return protocol;
    }
}
