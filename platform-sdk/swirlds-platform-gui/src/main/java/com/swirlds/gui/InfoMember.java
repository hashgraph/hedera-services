/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.gui;

import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about a member in a swirld running on an app.
 */
public class InfoMember extends InfoEntity {
    private final InfoSwirld swirld; // parent
    private final List<InfoState> states = new ArrayList<>(); // children

    private final long memberId;
    private final Platform platform;

    public InfoMember(InfoSwirld swirld, long memberId, Platform platform) {
        this.swirld = swirld;
        this.memberId = memberId;
        this.platform = platform;
        final Address address = platform.getSelfAddress();
        setName(address.getNickname() + " - " + address.getSelfName());
        swirld.getMembers().add(this);
    }

    public List<InfoState> getStates() {
        return states;
    }

    public Platform getPlatform() {
        return platform;
    }
}
