// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hints;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsScheme;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableHintsStoreTest {
    @Mock
    private ReadableHintsStore subject;

    @Test
    void onlyReadyToAdoptIfNextConstructionIsCompleteAndMatching() {
        final var rosterHash = Bytes.wrap("RH");
        doCallRealMethod().when(subject).isReadyToAdopt(rosterHash);

        given(subject.getNextConstruction()).willReturn(HintsConstruction.DEFAULT);
        assertFalse(subject.isReadyToAdopt(rosterHash));

        given(subject.getNextConstruction())
                .willReturn(HintsConstruction.newBuilder()
                        .targetRosterHash(rosterHash)
                        .build());
        assertFalse(subject.isReadyToAdopt(rosterHash));

        given(subject.getNextConstruction())
                .willReturn(HintsConstruction.newBuilder()
                        .targetRosterHash(rosterHash)
                        .hintsScheme(HintsScheme.DEFAULT)
                        .build());
        assertTrue(subject.isReadyToAdopt(rosterHash));
    }
}
