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

import java.util.List;

public interface PortForwarder {

    public void addListener(PortMappingListener listener);

    public void addPortMapping(String ip, int internalPort, int externalPort, Protocol protocol, String name);

    public void setPortMappings(List<PortMapping> portsToBeMapped);

    public void execute();

    public void refreshMappings();

    public String getExternalIPAddress();

    public boolean isSuccessful();

    public void closeService();

    public enum Protocol {
        TCP,
        UDP
    }
}
