/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.singleton;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.state.merkle.MerkleTestBase;
import org.junit.jupiter.api.Test;

class ValueLeafTest extends MerkleTestBase {
    @Test
    void setValue() {
        setupSingletonCountry();
        final var leaf = new ValueLeaf<>(countryMetadata, DENMARK);
        assertThat(leaf.getValue()).isEqualTo(DENMARK);
        leaf.setValue(FRANCE);
        assertThat(leaf.getValue()).isEqualTo(FRANCE);
    }
}
