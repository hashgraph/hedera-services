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

package com.hedera.node.app.service.mono.context;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.state.merkle.MerkleEntityId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({LogCaptureExtension.class, MockitoExtension.class})
class NodeInfoTest {
    private final long nodeId = 0L;

    @Mock
    private Address address;

    @Mock
    private AddressBook book;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private NodeInfo subject;

    @BeforeEach
    void setUp() {
        subject = new NodeInfo(nodeId, () -> book);
    }

    @Test
    void understandsNonZeroWeight() {
        givenEntryWithWeight(nodeId, 1L);

        // expect:
        assertFalse(subject.isZeroWeight(nodeId));
        assertFalse(subject.isSelfZeroWeight());
    }

    @Test
    void understandsZeroWeight() {
        givenEntryWithWeight(nodeId, 0L);

        // expect:
        assertTrue(subject.isZeroWeight(nodeId));
        assertTrue(subject.isSelfZeroWeight());
    }

    @Test
    void interpretsMissingAsZeroWeight() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.isZeroWeight(-1));
        assertThrows(IllegalArgumentException.class, () -> subject.isZeroWeight(1));
    }

    @Test
    void understandsAccountIsInMemo() {
        // setup:
        final var memo = "0.0.3";
        final var expectedAccount = IdUtils.asAccount(memo);
        final var expectedAccountKey = new MerkleEntityId(0, 0, 3);

        givenEntryWithMemoAndWeight(nodeId, memo, 1L);

        // expect:
        assertEquals(expectedAccount, subject.accountOf(nodeId));
        assertEquals(expectedAccount, subject.selfAccount());
        assertTrue(subject.hasSelfAccount());
        // and:
        assertEquals(EntityNum.fromAccountId(expectedAccount), subject.accountKeyOf(nodeId));
    }

    @Test
    void logsErrorOnMissingAccountForNonZeroWeight() {
        givenEntryWithMemoAndWeight(nodeId, "Oops!", 1L);

        // when:
        subject.readBook();

        // then:
        assertThat(
                logCaptor.errorLogs(),
                contains(startsWith("Cannot parse account for staked node id 0, potentially fatal")));
        assertFalse(subject.hasSelfAccount());
    }

    @Test
    void doesNotLogErrorOnMissingAccountForZeroWeight() {
        givenEntryWithMemoAndWeight(nodeId, "Oops!", 0L);

        // when:
        subject.readBook();

        // then:
        assertTrue(logCaptor.errorLogs().isEmpty());
    }

    @Test
    void throwsIseOnWeightedNodeNoAccount() {
        givenEntryWithMemoAndWeight(nodeId, "LULZ", 1L);

        // expect:
        assertThrows(IllegalStateException.class, subject::validateSelfAccountIfNonZeroWeight);
    }

    @Test
    void doesntThrowIseOnZeroWeightNodeNoAccount() {
        givenEntryWithMemoAndWeight(nodeId, "LULZ", 0L);

        // expect:
        assertDoesNotThrow(subject::validateSelfAccountIfNonZeroWeight);
    }

    @Test
    void doesntThrowIseOnWeightedNodeWithAccount() {
        givenEntryWithMemoAndWeight(nodeId, "0.0.3", 1L);

        // expect:
        assertDoesNotThrow(subject::validateSelfAccountIfNonZeroWeight);
    }

    @Test
    void throwsIaeOnMissingNode() {
        givenEntryWithMemoAndWeight(nodeId, "0.0.3", 1L);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.accountOf(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.accountOf(1L));
    }

    @Test
    void throwsIaeOnMissingAccount() {
        givenEntryWithMemoAndWeight(nodeId, "ZERO-STAKE", 0L);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.accountOf(nodeId));
    }

    @Test
    void validatesTheId() {
        givenEntryWithWeight(nodeId, 10L);
        assertEquals(true, subject.isValidId(nodeId));
        assertEquals(false, subject.isValidId(10L));
    }

    private void givenEntryWithWeight(long id, long weight) {
        given(address.getWeight()).willReturn(weight);
        given(address.getMemo()).willReturn("0.0." + (3 + id));
        given(book.getAddress(id)).willReturn(address);
        given(book.getSize()).willReturn(1);
    }

    private void givenEntryWithMemoAndWeight(long id, String memo, long weight) {
        given(address.getWeight()).willReturn(weight);
        given(address.getMemo()).willReturn(memo);
        given(book.getAddress(id)).willReturn(address);
        given(book.getSize()).willReturn(1);
    }
}
