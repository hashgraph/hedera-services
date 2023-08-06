package contract;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static contract.Erc721OperationsConstants.NEXT_ENTITY_NUM;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractContractXTest {
    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    abstract protected long nextEntityNum();

    abstract protected Map<AccountID, Account> initialAccounts();

    abstract protected  Map<Bytes, AccountID> initialAliases();

    private void setupFakeStates() {
        final var fakeHederaState = (FakeHederaState) scaffoldingComponent.hederaState();

        fakeHederaState.addService(
                EntityIdService.NAME,
                Map.of("ENTITY_ID", new AtomicReference<>(new EntityNumber(NEXT_ENTITY_NUM - 1L))));

        fakeHederaState.addService("RecordCache", Map.of("TransactionRecordQueue", new ArrayDeque<>()));

        fakeHederaState.addService(
                BlockRecordService.NAME,
                Map.of(
                        BlockRecordService.BLOCK_INFO_STATE_KEY, new AtomicReference<>(BlockInfo.DEFAULT),
                        BlockRecordService.RUNNING_HASHES_STATE_KEY, new AtomicReference<>(RunningHashes.DEFAULT)));

        fakeHederaState.addService(
                TokenService.NAME,
                Map.of(
                        TokenServiceImpl.ACCOUNTS_KEY, initialAccounts(),
                        TokenServiceImpl.ALIASES_KEY, initialAliases(),
                        TokenServiceImpl.TOKENS_KEY, new HashMap<>()));
        fakeHederaState.addService(
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, new HashMap<FileID, File>()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));
    }
}
