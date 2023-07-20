package com.hedera.node.app.service.contract.impl.test.exec.scope;

import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.QueryExtFrameScope;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class QueryExtFrameScopeTest {
    @Mock
    private QueryContext context;
    
    private QueryExtFrameScope subject;

    @BeforeEach
    void setUp() {
        subject = new QueryExtFrameScope(context);
    }

    @Test
    void doesNotSupportAnyMutations() {
        assertThrows(UnsupportedOperationException.class, () -> subject.setNonce(1L, 2L));
        assertThrows(UnsupportedOperationException.class, () -> subject.createHollowAccount(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.finalizeHollowAccountAsContract(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.finalizeHollowAccountAsContract(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.collectFee(1L, 2L));
        assertThrows(UnsupportedOperationException.class, () -> subject.refundFee(1L, 2L));
        assertThrows(UnsupportedOperationException.class, () -> subject.transferWithReceiverSigCheck(
                1L, 2L, 3L,
                new ActiveContractVerificationStrategy(1, Bytes.EMPTY, true)));
        assertThrows(UnsupportedOperationException.class, () -> subject.trackDeletion(1L, 2L));
    }
}