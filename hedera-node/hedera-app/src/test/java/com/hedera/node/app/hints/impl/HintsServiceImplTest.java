package com.hedera.node.app.hints.impl;

import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class HintsServiceImplTest {
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private ActiveRosters activeRosters;
    @Mock
    private WritableHintsStore hintsStore;
    @Mock
    private SchemaRegistry schemaRegistry;

    private final HintsServiceImpl subject = new HintsServiceImpl();

    @Test
    void nothingSupportedYet() {
        assertThrows(UnsupportedOperationException.class, () -> subject.reconcile(activeRosters, hintsStore, CONSENSUS_NOW));
        assertThrows(UnsupportedOperationException.class, () -> subject.registerSchemas(schemaRegistry));
        assertThrows(UnsupportedOperationException.class, subject::isReady);
        assertThrows(UnsupportedOperationException.class, () -> subject.signFuture(Bytes.EMPTY));
    }
}