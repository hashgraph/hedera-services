/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.internal;

import com.swirlds.common.system.address.Address;
import com.swirlds.platform.SwirldsPlatform;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about a member in a swirld running on an app.
 */
public class InfoMember extends InfoEntity {
    public InfoSwirld swirld; // parent
    List<InfoState> states = new ArrayList<InfoState>(); // children

    long memberId;
    SwirldsPlatform platform;

    public InfoMember(InfoSwirld swirld, long memberId, SwirldsPlatform platform) {
        this.swirld = swirld;
        this.memberId = memberId;
        this.platform = platform;
        final Address address = platform.getSelfAddress();
        this.name = address.getNickname() + " - " + address.getSelfName();
        swirld.members.add(this);
    }
}
