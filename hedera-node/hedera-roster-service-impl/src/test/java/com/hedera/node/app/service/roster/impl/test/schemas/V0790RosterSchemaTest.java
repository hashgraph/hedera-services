// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.roster.impl.test.schemas;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.roster.impl.schemas.V0790RosterSchema;
import com.hedera.node.app.spi.migrate.HederaMigrationContext;
import com.swirlds.state.spi.WritableStates;
import java.util.function.Function;
import org.hiero.base.crypto.Cryptography;
import org.hiero.consensus.roster.WritableRosterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V0790RosterSchemaTest {
    @Mock
    private HederaMigrationContext ctx;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableRosterStore rosterStore;

    @Mock
    private Function<WritableStates, WritableRosterStore> rosterStoreFactory;

    private V0790RosterSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0790RosterSchema(rosterStoreFactory);
    }

    @Test
    void createsNoStates() {
        assertThat(subject.statesToCreate()).isEmpty();
    }

    @Test
    void rehashesRostersOnUpgrade() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);
        given(ctx.isUpgrade(any())).willReturn(true);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verify(rosterStore).rehashRosters(Cryptography.DEFAULT_DIGEST_TYPE);
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void noOpIfNotUpgrade() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);

        subject.restart(ctx);

        verifyNoInteractions(rosterStoreFactory);
        verifyNoInteractions(rosterStore);
    }

    @Test
    void noOpOnGenesis() {
        given(ctx.isGenesis()).willReturn(true);

        subject.restart(ctx);

        verifyNoInteractions(rosterStoreFactory);
        verifyNoInteractions(rosterStore);
    }
}
