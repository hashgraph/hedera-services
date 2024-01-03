/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about a swirld running on an app.
 */
public class InfoSwirld extends InfoEntity {

    private final InfoApp app; // parent

    private final List<InfoMember> members = new ArrayList<>(); // children

    public InfoSwirld(InfoApp app, byte[] swirldIdBytes) {
        super("Swirld " + new Reference(swirldIdBytes).to62Prefix());
        this.app = app;
        this.app.getSwirlds().add(this);
    }

    public InfoApp getApp() {
        return app;
    }

    public List<InfoMember> getMembers() {
        return members;
    }
}
