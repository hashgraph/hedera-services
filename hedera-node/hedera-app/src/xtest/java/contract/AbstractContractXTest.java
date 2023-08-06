package contract;

import com.esaulpaugh.headlong.abi.Address;
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractContractXTest {
    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void scenarioPasses() {
        setupInitialStates();

        handleAndCommitScenarioTransactions();

        assertExpectedAliases(finalAliases());
        assertExpectedAccounts(finalAccounts());
        assertExpectedBytecodes(finalBytecodes());
        assertExpectedStorage(finalStorage(), finalAccounts());
    }

    abstract protected long initialEntityNum();

    abstract protected Map<FileID, File> initialFiles();

    abstract protected Map<Bytes, AccountID> initialAliases();

    abstract protected Map<AccountID, Account> initialAccounts();

    abstract protected void handleAndCommitScenarioTransactions();

    abstract protected void assertExpectedStorage(
            @NonNull ReadableKVState<SlotKey, SlotValue> storage,
            @NonNull ReadableKVState<AccountID, Account> accounts);
    abstract protected void assertExpectedAliases(@NonNull ReadableKVState<Bytes, AccountID> aliases);
    abstract protected void assertExpectedAccounts(@NonNull ReadableKVState<AccountID, Account> accounts);
    abstract protected void assertExpectedBytecodes(@NonNull ReadableKVState<EntityNumber, Bytecode> bytecodes);

    protected void handleAndCommit(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = scaffoldingComponent.contextFactory().apply(txn);
            handler.handle(context);
            ((SavepointStackImpl) context.savepointStack()).commit();
        }
    }

    protected Bytes resourceAsBytes(@NonNull final String loc) {
        try {
            try (final var in = AbstractContractXTest.class.getClassLoader().getResourceAsStream(loc)) {
                final var bytes = Objects.requireNonNull(in).readAllBytes();
                return Bytes.wrap(bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Address addressOf(@NonNull final Bytes address) {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(1, address.toByteArray())));
    }

    private void setupInitialStates() {
        final var fakeHederaState = (FakeHederaState) scaffoldingComponent.hederaState();

        fakeHederaState.addService(
                EntityIdService.NAME,
                Map.of("ENTITY_ID", new AtomicReference<>(new EntityNumber(initialEntityNum()))));

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
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, initialFiles()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));

        scaffoldingComponent.workingStateAccessor().setHederaState(fakeHederaState);
    }

    private ReadableKVState<Bytes, AccountID> finalAliases() {
        return scaffoldingComponent
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ALIASES_KEY);
    }

    private ReadableKVState<SlotKey, SlotValue> finalStorage() {
        return scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.STORAGE_KEY);
    }

    private ReadableKVState<EntityNumber, Bytecode> finalBytecodes() {
        return scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.BYTECODE_KEY);
    }

    private ReadableKVState<AccountID, Account> finalAccounts() {
        return scaffoldingComponent
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
    }
}
