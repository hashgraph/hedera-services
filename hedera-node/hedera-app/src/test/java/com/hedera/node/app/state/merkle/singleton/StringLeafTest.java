/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.state.merkle.MerkleTestBase;
import org.junit.jupiter.api.Test;

class StringLeafTest extends MerkleTestBase {
    @Test
    void setValue() {
        final var leaf = new StringLeaf("Label 1");
        assertThat(leaf.getLabel()).isEqualTo("Label 1");
        leaf.setLabel("Label 2");
        assertThat(leaf.getLabel()).isEqualTo("Label 2");
    }

    @Test
    void labelTooLong() {
        final var label = randomString(2000);
        assertThatThrownBy(() -> new StringLeaf(label))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length");
    }
}
