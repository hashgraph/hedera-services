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
package com.hedera.node.app.service.mono.utils.subjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import java.time.Instant;
import java.util.Objects;

public class UniqueTokenValueSubject {

    /** Convenience function for mimicing assertThat for UniqueTokenValue. */
    public static UniqueTokenValueSubject assertThatTokenValue(final UniqueTokenValue actual) {
        return new UniqueTokenValueSubject(actual);
    }

    private final UniqueTokenValue actual;

    private UniqueTokenValueSubject(final UniqueTokenValue actual) {
        this.actual = Objects.requireNonNull(actual);
    }

    public void hasOwner(final long expected) {
        assertThat(actual.getOwner().num()).isEqualTo(expected);
    }

    public void hasOwner(final EntityId expected) {
        assertThat(actual.getOwner()).isEqualTo(expected);
    }

    public void hasSpender(final long expected) {
        assertThat(actual.getSpender().num()).isEqualTo(expected);
    }

    public void hasSpender(final EntityId expected) {
        assertThat(actual.getSpender()).isEqualTo(expected);
    }

    public void hasPackedCreationTime(final long expected) {
        assertThat(actual.getPackedCreationTime()).isEqualTo(expected);
    }

    public void hasCreationTime(final RichInstant expected) {
        assertThat(actual.getCreationTime()).isEqualTo(expected);
    }

    public void hasCreationTime(final Instant expected) {
        hasCreationTime(RichInstant.fromJava(expected));
    }

    public void hasMetadata(final byte[] expected) {
        assertThat(actual.getMetadata()).isEqualTo(expected);
    }

    public void hasPrev(final long tokenNum, final long serialNum) {
        assertThat(actual.getPrev()).isEqualTo(new NftNumPair(tokenNum, serialNum));
    }

    public void hasPrev(final NftNumPair expected) {
        assertThat(actual.getPrev()).isEqualTo(expected);
    }

    public void hasNext(final long tokenNum, final long serialNum) {
        assertThat(actual.getNext()).isEqualTo(new NftNumPair(tokenNum, serialNum));
    }

    public void hasNext(final NftNumPair expected) {
        assertThat(actual.getNext()).isEqualTo(expected);
    }

    public void isImmutable() {
        assertThat(actual.isImmutable()).isTrue();
    }

    public void isNotImmutable() {
        assertThat(actual.isImmutable()).isFalse();
    }
}
