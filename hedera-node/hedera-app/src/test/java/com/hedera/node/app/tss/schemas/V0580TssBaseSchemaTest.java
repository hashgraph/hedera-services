/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.schemas;

import static com.hedera.hapi.node.state.tss.RosterToKey.ACTIVE_ROSTER;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.WAITING_FOR_ENCRYPTION_KEYS;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.tss.TssStatus;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V0580TssBaseSchemaTest {
    @Mock
    private WritableStates writableStates;

    @Mock
    private MigrationContext ctx;

    @Mock
    private WritableSingletonState<TssStatus> tssStatusState;

    private final V0580TssBaseSchema subject = new V0580TssBaseSchema();

    @Test
    void missingStatusSingletonReceivesDefaultAtMigration() {
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<TssStatus>getSingleton(TSS_STATUS_KEY)).willReturn(tssStatusState);

        subject.migrate(ctx);

        final var captor = ArgumentCaptor.forClass(TssStatus.class);
        verify(tssStatusState).put(captor.capture());
        final var status = captor.getValue();
        assertThat(status.ledgerId().length()).isEqualTo(0L);
        assertThat(status.tssKeyingStatus()).isEqualTo(WAITING_FOR_ENCRYPTION_KEYS);
        assertThat(status.rosterToKey()).isEqualTo(ACTIVE_ROSTER);
    }

    @Test
    void presentStatusSingletonIsNotTouched() {
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<TssStatus>getSingleton(TSS_STATUS_KEY)).willReturn(tssStatusState);
        given(tssStatusState.get()).willReturn(TssStatus.DEFAULT);

        subject.migrate(ctx);

        verify(tssStatusState, never()).put(any());
    }
}
