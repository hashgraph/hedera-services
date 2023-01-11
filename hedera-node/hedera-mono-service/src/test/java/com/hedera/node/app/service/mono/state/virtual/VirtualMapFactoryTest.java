/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import org.junit.jupiter.api.Test;

class VirtualMapFactoryTest {

    @Test
    void virtualizedUniqueTokenStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMapFactory subject = new VirtualMapFactory();

        final var map = subject.newVirtualizedUniqueTokenStorage();
        assertThat(map.isEmpty()).isTrue();

        map.put(
                new UniqueTokenKey(123L, 456L),
                new UniqueTokenValue(
                        789L, 123L, "hello world".getBytes(), RichInstant.MISSING_INSTANT));

        assertThat(map.get(new UniqueTokenKey(123L, 111L))).isNull();
        final var value = map.get(new UniqueTokenKey(123L, 456L));
        assertThat(value).isNotNull();
        assertThat(value.getOwnerAccountNum()).isEqualTo(789L);
        assertThat(value.getMetadata()).isEqualTo("hello world".getBytes());
    }
}
