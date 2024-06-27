/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.keys;

import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hederahashgraph.api.proto.java.Key;

public class KeyTree {
    private final KeyTreeNode root;

    private KeyTree(final KeyTreeNode root) {
        this.root = root;
    }

    public static KeyTree withRoot(final NodeFactory rootFactory) {
        return new KeyTree(KeyTreeNode.from(rootFactory));
    }

    public Key asKey() {
        return asKey(KeyFactory.getDefaultInstance());
    }

    public com.hedera.hapi.node.base.Key asPbjKey() {
        return CommonPbjConverters.protoToPbj(asKey(), com.hedera.hapi.node.base.Key.class);
    }

    public Key asKey(final KeyFactory factoryToUse) {
        return root.asKey(factoryToUse);
    }
}
