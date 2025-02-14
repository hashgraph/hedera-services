// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SyntheticIds;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticIdsTest {
    @Mock
    private HederaNativeOperations nativeOperations;

    private final SyntheticIds implicitSubject = new SyntheticIds();

    @Test
    void returnsNumericIdIfAddressIsCanonicalReference() {
        given(nativeOperations.resolveAlias(ConversionUtils.tuweniToPbjBytes(EIP_1014_ADDRESS)))
                .willReturn(TestHelpers.A_NEW_ACCOUNT_ID.accountNumOrThrow());
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(ALIASED_SOMEBODY);
        final var subject = implicitSubject.converterFor(nativeOperations);
        final var synthId = subject.convert(asHeadlongAddress(EIP_1014_ADDRESS));
        assertEquals(TestHelpers.A_NEW_ACCOUNT_ID, synthId);
    }

    @Test
    void returnsNumericIdIfMissingLongZeroDebit() {
        final var missingLongZeroAddress = asHeadlongAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        final var subject = implicitSubject.converterFor(nativeOperations);
        final var synthId = subject.convert(missingLongZeroAddress);
        assertEquals(TestHelpers.A_NEW_ACCOUNT_ID, synthId);
    }

    @Test
    void returnsAliasIdIfMissingLongZeroCredit() {
        final var expectedId = AccountID.newBuilder()
                .alias(Bytes.wrap(asEvmAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow())))
                .build();
        final var missingLongZeroAddress = asHeadlongAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        final var subject = implicitSubject.converterFor(nativeOperations);
        final var synthId = subject.convertCredit(missingLongZeroAddress);
        assertEquals(expectedId, synthId);
    }

    @Test
    void returnsGuaranteedFailLazyCreateIfMissingLongZeroCredit() {
        final var expectedId = AccountID.newBuilder()
                .alias(Bytes.wrap(asEvmAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow())))
                .build();
        final var missingLongZeroAddress = asHeadlongAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        final var subject = implicitSubject.converterFor(nativeOperations);
        final var synthId = subject.convertCredit(missingLongZeroAddress);
        assertEquals(expectedId, synthId);
    }

    @Test
    void returnsLazyCreateToZeroAddressIfLongZeroCreditWithNonCanonicalReference() {
        final var expectedId =
                AccountID.newBuilder().alias(Bytes.wrap(asEvmAddress(0L))).build();
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(ALIASED_SOMEBODY);
        final var nonCanonicalLongZeroAddress = asHeadlongAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        final var subject = implicitSubject.converterFor(nativeOperations);
        final var synthId = subject.convertCredit(nonCanonicalLongZeroAddress);
        assertEquals(expectedId, synthId);
    }
}
