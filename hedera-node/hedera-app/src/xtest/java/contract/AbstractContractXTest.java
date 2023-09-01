/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
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
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for {@code xtest} scenarios that focus on contract operations.
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractContractXTest {
    static final long GAS_TO_OFFER = 2_000_000L;
    static final Duration STANDARD_AUTO_RENEW_PERIOD = new Duration(7776000L);

    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void scenarioPasses() {
        setupFeeManager();
        setupInitialStates();

        doScenarioOperations();

        assertExpectedAliases(finalAliases());
        assertExpectedAccounts(finalAccounts());
        assertExpectedBytecodes(finalBytecodes());
        assertExpectedStorage(finalStorage(), finalAccounts());
    }

    protected abstract long initialEntityNum();

    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>();
    }

    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        return new HashMap<>();
    }

    protected Map<NftID, Nft> initialNfts() {
        return new HashMap<>();
    }

    protected abstract Map<FileID, File> initialFiles();

    protected abstract Map<ProtoBytes, AccountID> initialAliases();

    protected abstract Map<AccountID, Account> initialAccounts();

    protected RunningHashes initialRunningHashes() {
        return RunningHashes.DEFAULT;
    }

    protected abstract void doScenarioOperations();

    protected void assertExpectedStorage(
            @NonNull ReadableKVState<SlotKey, SlotValue> storage,
            @NonNull ReadableKVState<AccountID, Account> accounts) {}

    protected void assertExpectedAliases(@NonNull ReadableKVState<ProtoBytes, AccountID> aliases) {}

    protected void assertExpectedAccounts(@NonNull ReadableKVState<AccountID, Account> accounts) {}

    protected void assertExpectedBytecodes(@NonNull ReadableKVState<EntityNumber, Bytecode> bytecodes) {}

    protected void handleAndCommit(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = scaffoldingComponent.txnContextFactory().apply(txn);
            handler.handle(context);
            ((SavepointStackImpl) context.savepointStack()).commitFullStack();
        }
    }

    protected void answerSingleQuery(
            @NonNull final QueryHandler handler,
            @NonNull final Query query,
            @NonNull final AccountID payerId,
            @NonNull final Consumer<Response> assertions) {
        final var context = scaffoldingComponent.queryContextFactory().apply(query, payerId);
        assertions.accept(handler.findResponse(context, ResponseHeader.DEFAULT));
    }

    protected void handleAndCommitSingleTransaction(
            @NonNull final TransactionHandler handler, @NonNull final TransactionBody txn) {
        handleAndCommitSingleTransaction(handler, txn, ResponseCodeEnum.SUCCESS);
    }

    protected void handleAndCommitSingleTransaction(
            @NonNull final TransactionHandler handler,
            @NonNull final TransactionBody txn,
            @NonNull final ResponseCodeEnum expectedStatus) {
        final var context = scaffoldingComponent.txnContextFactory().apply(txn);
        handler.handle(context);
        ((SavepointStackImpl) context.savepointStack()).commitFullStack();
        final var recordBuilder = context.recordBuilder(SingleTransactionRecordBuilder.class);
        assertEquals(expectedStatus, recordBuilder.status());
    }

    protected TransactionBody createCallTransactionBody(
            final AccountID payer,
            final long value,
            @NonNull final ContractID contractId,
            @NonNull final ByteBuffer encoded) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(createContractCallTransactionBody(value, contractId, encoded))
                .build();
    }

    protected ContractCallTransactionBody createContractCallTransactionBody(
            final long value, @NonNull final ContractID contractId, @NonNull final ByteBuffer encoded) {
        return ContractCallTransactionBody.newBuilder()
                .functionParameters(Bytes.wrap(encoded.array()))
                .contractID(contractId)
                .amount(value)
                .gas(GAS_TO_OFFER)
                .build();
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

    protected Consumer<Response> assertingCallLocalResultIs(@NonNull final Bytes expectedResult) {
        return response -> assertEquals(
                expectedResult,
                response.contractCallLocalOrThrow().functionResultOrThrow().contractCallResult());
    }

    private void setupFeeManager() {
        var feeScheduleBytes = resourceAsBytes("feeSchedules.bin");
        scaffoldingComponent.feeManager().update(feeScheduleBytes);
    }

    private void setupInitialStates() {
        final var fakeHederaState = (FakeHederaState) scaffoldingComponent.hederaState();

        fakeHederaState.addService(
                EntityIdService.NAME, Map.of("ENTITY_ID", new AtomicReference<>(new EntityNumber(initialEntityNum()))));

        fakeHederaState.addService("RecordCache", Map.of("TransactionRecordQueue", new ArrayDeque<>()));

        fakeHederaState.addService(
                BlockRecordService.NAME,
                Map.of(
                        BlockRecordService.BLOCK_INFO_STATE_KEY, new AtomicReference<>(BlockInfo.DEFAULT),
                        BlockRecordService.RUNNING_HASHES_STATE_KEY, new AtomicReference<>(initialRunningHashes())));

        fakeHederaState.addService(
                TokenService.NAME,
                Map.of(
                        TokenServiceImpl.TOKEN_RELS_KEY, initialTokenRelationships(),
                        TokenServiceImpl.ACCOUNTS_KEY, initialAccounts(),
                        TokenServiceImpl.ALIASES_KEY, initialAliases(),
                        TokenServiceImpl.TOKENS_KEY, initialTokens(),
                        TokenServiceImpl.NFTS_KEY, initialNfts()));
        fakeHederaState.addService(FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, initialFiles()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));

        scaffoldingComponent.workingStateAccessor().setHederaState(fakeHederaState);
    }

    private ReadableKVState<ProtoBytes, AccountID> finalAliases() {
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

    public static com.esaulpaugh.headlong.abi.Address asHeadlongAddress(final byte[] address) {
        final var addressBytes = org.apache.tuweni.bytes.Bytes.wrap(address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(addressAsInteger));
    }
}
