package com.hedera.node.app.service.contract.impl.test.state;

import com.hedera.node.app.service.contract.impl.infra.RentCalculator;
import com.hedera.node.app.service.contract.impl.infra.StorageLinkedLists;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.service.contract.impl.state.BaseProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.StorageChange;
import com.hedera.node.app.service.contract.impl.state.StorageChanges;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.Fees;
import com.hedera.node.app.spi.meta.bni.Scope;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BaseProxyWorldUpdaterTest {
    private static final long A_NUM = 123L;
    private static final long B_NUM = 234L;

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
    private StorageLinkedLists linkedLists;
    @Mock
    private StorageSizeValidator storageSizeValidator;
    @Mock
    private EvmFrameState evmFrameState;
    @Mock
    private EvmFrameStateFactory evmFrameStateFactory;
    @Mock
    private Fees fees;
    @Mock
    private Scope scope;
    @Mock
    private Dispatch dispatch;

    private BaseProxyWorldUpdater subject;

    @BeforeEach
    void setUp() {
        subject = new BaseProxyWorldUpdater(
                scope, evmFrameStateFactory, rentCalculator, linkedLists, storageSizeValidator);
    }

    @Test
    void summarizesPendingChangesAsExpected() {
        InOrder inOrder = BDDMockito.inOrder(storageSizeValidator, linkedLists, rentCalculator, scope);
        final var sizeIncludingPendingRemovals = 123L;
        // Three keys being removed in pending changes
        final var sizeExcludingPendingRemovals = sizeIncludingPendingRemovals - 3;
        given(evmFrameStateFactory.createIn(scope)).willReturn(evmFrameState);
        given(evmFrameState.getKvStateSize()).willReturn(sizeIncludingPendingRemovals);
        given(evmFrameState.getPendingStorageChanges()).willReturn(pendingChanges());

        subject.commit();

        inOrder.verify(storageSizeValidator).assertValid(
                sizeExcludingPendingRemovals, scope, expectedSizeChanges());
    }

    private List<StorageChanges> pendingChanges() {
        return List.of(
                new StorageChanges(A_NUM, List.of(
                        new StorageChange(A_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                        new StorageChange(A_KEY_BEING_CHANGED, UInt256.ONE, UInt256.MAX_VALUE),
                        new StorageChange(A_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ZERO),
                        new StorageChange(A_SECOND_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                        new StorageChange(A_THIRD_KEY_BEING_ADDED, UInt256.ZERO, UInt256.MAX_VALUE))),
                new StorageChanges(B_NUM, List.of(
                        new StorageChange(B_KEY_BEING_ADDED, UInt256.ZERO, UInt256.ONE),
                        new StorageChange(B_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ZERO),
                        new StorageChange(B_SECOND_KEY_BEING_REMOVED, UInt256.ONE, UInt256.ONE))));
    }

    private List<StorageSizeChange> expectedSizeChanges() {
        return List.of(
                new StorageSizeChange(A_NUM, 2),
                new StorageSizeChange(B_NUM, -1));
    }
}