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

package com.hedera.node.app.service.contract.impl.test.state;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.infra.IterableStorageManager;
import com.hedera.node.app.service.contract.impl.infra.RentCalculator;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.RentFactors;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RootProxyWorldUpdaterTest {
    private static final ContractID A_CONTRAC_ID =
            ContractID.newBuilder().contractNum(123L).build();
    private static final ContractID B_CONTRAC_ID =
            ContractID.newBuilder().contractNum(234L).build();
    private static final UInt256 A_KEY_BEING_ADDED = UInt256.fromHexString("0x1234");
    private static final UInt256 A_KEY_BEING_CHANGED = UInt256.fromHexString("0x2345");
    private static final UInt256 A_KEY_BEING_REMOVED = UInt256.fromHexString("0x3456");
    private static final UInt256 A_SECOND_KEY_BEING_ADDED = UInt256.fromHexString("0x4567");
    private static final UInt256 A_THIRD_KEY_BEING_ADDED = UInt256.fromHexString("0x7654");
    private static final UInt256 B_KEY_BEING_ADDED = UInt256.fromHexString("0x5678");
    private static final UInt256 B_KEY_BEING_REMOVED = UInt256.fromHexString("0x6789");
    private static final UInt256 B_SECOND_KEY_BEING_REMOVED = UInt256.fromHexString("0x7890");

    @Mock
    private RentCalculator rentCalculator;

    @Mock
    private IterableStorageManager storageManager;

    @Mock
    private StorageSizeValidator storageSizeValidator;

    @Mock
    private HandleContext context;

    @Mock
    private EvmFrameState evmFrameState;

    @Mock
    private HederaOperations hederaOperations;

    @Mock
    private HederaNativeOperations hederaNativeOperations;

    @Mock
    private SystemContractOperations systemContractOperations;

    @Mock
    private ContractStateStore store;

    @Mock
    private ThrottleAdviser throttleAdviser;

    private Enhancement enhancement;
    private RootProxyWorldUpdater subject;

    @BeforeEach
    void setup() {
        enhancement =
                new HederaWorldUpdater.Enhancement(hederaOperations, hederaNativeOperations, systemContractOperations);
        givenSubjectWith(HederaTestConfigBuilder.create().getOrCreateConfig(), enhancement);
    }

    @Test
    void refusesToReturnCommittedChangesWithoutSuccessfulCommit() {
        assertThrows(IllegalStateException.class, subject::getUpdatedContractNonces);
        assertThrows(IllegalStateException.class, subject::getCreatedContractIds);
    }

    @Test
    void performsAdditionalCommitActionsInOrder() {
        InOrder inOrder = BDDMockito.inOrder(storageSizeValidator, storageManager, rentCalculator, hederaOperations);

        final var aExpiry = 1_234_567;
        final var aSlotsUsedBeforeCommit = 101;
        final var sizeIncludingPendingRemovals = 123L;
        // Three keys being removed in pending changes
        final var sizeExcludingPendingRemovals = sizeIncludingPendingRemovals - 3;
        given(evmFrameState.getKvStateSize()).willReturn(sizeIncludingPendingRemovals);
        given(evmFrameState.getStorageChanges()).willReturn(pendingChanges());
        given(evmFrameState.getRentFactorsFor(A_CONTRAC_ID))
                .willReturn(new RentFactors(aSlotsUsedBeforeCommit, aExpiry));

        final var rentInTinycents = 666_666L;
        final var rentInTinybars = 111_111L;
        // A contract is allocating 2 slots net
        given(rentCalculator.computeFor(sizeExcludingPendingRemovals, 2, aSlotsUsedBeforeCommit, aExpiry))
                .willReturn(rentInTinycents);
        given(hederaOperations.valueInTinybars(rentInTinycents)).willReturn(rentInTinybars);
        given(hederaOperations.getStore()).willReturn(store);
        final var createdIds = new ArrayList<>(List.of(CALLED_CONTRACT_ID));
        final var updatedNonces = new ArrayList<>(List.of(new ContractNonceInfo(CALLED_CONTRACT_ID, 1L)));
        given(hederaOperations.summarizeContractChanges())
                .willReturn(new ContractChangeSummary(createdIds, updatedNonces));

        subject.commit();

        inOrder.verify(storageSizeValidator)
                .assertValid(sizeExcludingPendingRemovals, hederaOperations, expectedSizeChanges());
        inOrder.verify(hederaOperations).chargeStorageRent(A_CONTRAC_ID, rentInTinybars, true);
        inOrder.verify(storageManager).persistChanges(enhancement, pendingChanges(), expectedSizeChanges(), store);
        inOrder.verify(hederaOperations).commit();

        assertSame(createdIds, subject.getCreatedContractIds());
        assertSame(updatedNonces, subject.getUpdatedContractNonces());
    }

    private void givenSubjectWith(@NonNull final Configuration configuration, @NonNull final Enhancement enhancement) {
        subject = new RootProxyWorldUpdater(
                enhancement,
                configuration.getConfigData(ContractsConfig.class),
                () -> evmFrameState,
                rentCalculator,
                storageManager,
                storageSizeValidator,
                context);
    }

    private List<StorageAccesses> pendingChanges() {
        return List.of(
                new StorageAccesses(
                        A_CONTRAC_ID,
                        List.of(
                                new StorageAccess(A_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                                new StorageAccess(A_KEY_BEING_CHANGED, UInt256.ONE, UInt256.MAX_VALUE),
                                new StorageAccess(A_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ZERO),
                                new StorageAccess(A_SECOND_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                                new StorageAccess(A_THIRD_KEY_BEING_ADDED, UInt256.ZERO, UInt256.MAX_VALUE))),
                new StorageAccesses(
                        B_CONTRAC_ID,
                        List.of(
                                new StorageAccess(B_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                                new StorageAccess(B_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ZERO),
                                new StorageAccess(B_SECOND_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ZERO))));
    }

    private List<StorageSizeChange> expectedSizeChanges() {
        return List.of(new StorageSizeChange(A_CONTRAC_ID, 1, 3), new StorageSizeChange(B_CONTRAC_ID, 2, 1));
    }
}
