package com.hedera.node.app.service.consensus.impl.test.schemas;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.schemas.InitialModServiceConsensusSchema;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.networkadmin.impl.schemas.InitialModServiceAdminSchema;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.StateDefinition;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
public class InitialModServiceConsensusSchemaTest {

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private MerkleMap<EntityNum, MerkleTopic> fs;

    @Mock
    private WritableKVState ctx;

    @LoggingSubject
    private InitialModServiceConsensusSchema subject;


    @BeforeEach
    void setUp() {
        subject = new InitialModServiceConsensusSchema(CURRENT_VERSION);
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate.size()).isEqualTo(1);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(TOPICS_KEY, iter.next());
    }


    @Test
    void checkFSSetupCorrectly() {
        assertThatCode(() -> subject.setFromState(fs)).doesNotThrowAnyException();
    }

    @Test
    void setFSasExpectedAndHappyPathMigration() {
        subject.setFromState(fs);

        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.get(TOPICS_KEY)).willReturn(ctx);

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("BBM: running consensus migration...");
        assertThat(logCaptor.infoLogs()).contains("BBM: finished consensus service migration");


    }

    @Test
    void setEmptyFSasExpectedAndHappyPathMigration() {

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.warnLogs()).contains("BBM: no consensus 'from' state found");

    }

}
