/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Callback interface for responding to a migration event on the {@link MerkleHederaState}. This
 * callback is implemented by the {@link com.hedera.node.app.Hedera} application to iterate over all
 * {@link MerkleSchemaRegistry} instances to kick off migration.
 */
public interface MerkleMigrationHandler {
    /**
     * Given a mutable {@link MerkleHederaState}, initiate migration.
     *
     * @param tree The mutable merkle tree
     * @param version The version as supplied by the Hashgraph Platform.
     */
    void accept(@NonNull MerkleHederaState tree, int version);
}
