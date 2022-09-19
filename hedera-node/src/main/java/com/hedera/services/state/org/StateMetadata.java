/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.org;

import com.google.protobuf.ByteString;
import com.hedera.services.ServicesApp;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.FastCopyable;
import com.swirlds.fchashmap.FCHashMap;
import java.util.Map;

/**
 * Contains the part of the Hedera Services world state that does influence handling of consensus
 * transactions, but is not hashed or serialized.
 */
public class StateMetadata implements FastCopyable {
    private final ServicesApp app;
    private final FCHashMap<ByteString, EntityNum> aliases;

    public StateMetadata(final ServicesApp app, final FCHashMap<ByteString, EntityNum> aliases) {
        this.app = app;
        this.aliases = aliases;
    }

    private StateMetadata(final StateMetadata that) {
        this.aliases = that.aliases.copy();
        this.app = that.app;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StateMetadata copy() {
        return new StateMetadata(this);
    }

    @Override
    public boolean release() {
        if (!aliases.isDestroyed()) {
            return aliases.release();
        } else {
            return false;
        }
    }

    public ServicesApp app() {
        return app;
    }

    public Map<ByteString, EntityNum> aliases() {
        return aliases;
    }
}
