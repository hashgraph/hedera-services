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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static contract.XTestConstants.PLACEHOLDER_CALL_BODY;
import static contract.XTestConstants.SET_OF_TRADITIONAL_RATES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

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
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAddressChecks;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttemptFactory;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
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
    private static final VerificationStrategies DEFAULT_VERIFICATION_STRATEGIES = new VerificationStrategies();
    static final long GAS_TO_OFFER = 2_000_000L;
    static final Duration STANDARD_AUTO_RENEW_PERIOD = new Duration(7776000L);

    @Mock
    private Metrics metrics;

    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater proxyUpdater;

    @Mock
    private HtsCallAddressChecks addressChecks;

    private HtsCallAttemptFactory callAttemptFactory;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
        callAttemptFactory = new HtsCallAttemptFactory(addressChecks, DEFAULT_VERIFICATION_STRATEGIES);
    }

    @Test
    void scenarioPasses() {
        setupFeeManager();
        setupInitialStates();
        setupExchangeManager();

        doScenarioOperations();

        assertExpectedAliases(finalAliases());
        assertExpectedAccounts(finalAccounts());
        assertExpectedBytecodes(finalBytecodes());
        assertExpectedStorage(finalStorage(), finalAccounts());
    }

    protected long initialEntityNum() {
        // An x-test that doesn't override this can't create entities
        return Long.MAX_VALUE;
    }

    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>();
    }

    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        return new HashMap<>();
    }

    protected Map<NftID, Nft> initialNfts() {
        return new HashMap<>();
    }

    protected Map<FileID, File> initialFiles() {
        // An x-test that doesn't use external initcode in HAPI ops won't need any files
        return new HashMap<>();
    }

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

    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {}

    protected void assertExpectedAccounts(@NonNull ReadableKVState<AccountID, Account> accounts) {}

    protected void assertExpectedBytecodes(@NonNull ReadableKVState<EntityNumber, Bytecode> bytecodes) {}

    protected void handleAndCommit(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = scaffoldingComponent.txnContextFactory().apply(txn);
            handler.handle(context);
            ((SavepointStackImpl) context.savepointStack()).commitFullStack();
        }
    }

    protected void runHtsCallAndExpectOnSuccess(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions) {
        runHtsCallAndExpectOnSuccess(false, sender, input, outputAssertions);
    }

    protected void runDelegatedHtsCallAndExpectOnSuccess(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions) {
        runHtsCallAndExpectOnSuccess(true, sender, input, outputAssertions);
    }

    private void runHtsCallAndExpectOnSuccess(
            final boolean requiresDelegatePermission,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions) {
        runHtsCallAndExpect(requiresDelegatePermission, sender, input, resultOnlyAssertion(result -> {
            assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
            outputAssertions.accept(result.getOutput());
        }));
    }

    protected void runHtsCallAndExpectRevert(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final ResponseCodeEnum status) {
        runHtsCallAndExpect(false, sender, input, resultOnlyAssertion(result -> {
            assertEquals(MessageFrame.State.REVERT, result.getState());
            final var impliedReason =
                    org.apache.tuweni.bytes.Bytes.wrap(status.protoName().getBytes(StandardCharsets.UTF_8));
            assertEquals(impliedReason, result.getOutput());
        }));
    }

    private void runHtsCallAndExpectRevert(
            final boolean requiresDelegatePermission,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final ResponseCodeEnum status) {
        runHtsCallAndExpect(requiresDelegatePermission, sender, input, resultOnlyAssertion(result -> {
            assertEquals(MessageFrame.State.REVERT, result.getState());
            final var impliedReason =
                    org.apache.tuweni.bytes.Bytes.wrap(status.protoName().getBytes(StandardCharsets.UTF_8));
            assertEquals(impliedReason, result.getOutput());
        }));
    }

    private void runHtsCallAndExpect(
            final boolean requiresDelegatePermission,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<HtsCall.PricedResult> resultAssertions) {
        final var context = scaffoldingComponent.txnContextFactory().apply(PLACEHOLDER_CALL_BODY);
        final var enhancement = new HederaWorldUpdater.Enhancement(
                new HandleHederaOperations(scaffoldingComponent.config().getConfigData(LedgerConfig.class), context),
                new HandleHederaNativeOperations(context),
                new HandleSystemContractOperations(context));
        given(proxyUpdater.enhancement()).willReturn(enhancement);
        given(frame.getWorldUpdater()).willReturn(proxyUpdater);
        given(frame.getSenderAddress()).willReturn(sender);
        given(addressChecks.hasParentDelegateCall(frame)).willReturn(requiresDelegatePermission);

        final var call = callAttemptFactory.createCallFrom(input, frame);

        final var pricedResult = call.execute();
        resultAssertions.accept(pricedResult);
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
                FeeService.NAME, Map.of("MIDNIGHT_RATES", new AtomicReference<>(SET_OF_TRADITIONAL_RATES)));

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
        fakeHederaState.addService(
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, initialFilesWithExchangeRate()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));

        scaffoldingComponent.workingStateAccessor().setHederaState(fakeHederaState);
    }

    private Map<FileID, File> initialFilesWithExchangeRate() {
        final var scenarioFiles = initialFiles();
        scenarioFiles.put(
                FileID.newBuilder().fileNum(112).build(),
                File.newBuilder()
                        .contents(ExchangeRateSet.PROTOBUF.toBytes(SET_OF_TRADITIONAL_RATES))
                        .build());
        return scenarioFiles;
    }

    private void setupExchangeManager() {
        final var state = Objects.requireNonNull(
                scaffoldingComponent.workingStateAccessor().getHederaState());
        final var midnightRates = state.createReadableStates(FeeService.NAME)
                .<ExchangeRateSet>getSingleton("MIDNIGHT_RATES")
                .get();

        scaffoldingComponent.exchangeRateManager().init(state, ExchangeRateSet.PROTOBUF.toBytes(midnightRates));
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

    private ReadableKVState<EntityIDPair, TokenRelation> finalTokenRelations() {
        return scaffoldingComponent
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.TOKEN_RELS_KEY);
    }

    private Consumer<HtsCall.PricedResult> resultOnlyAssertion(
            @NonNull final Consumer<PrecompiledContract.PrecompileContractResult> resultAssertion) {
        return pricedResult -> {
            final var fullResult = pricedResult.fullResult();
            final var result = fullResult.result();
            resultAssertion.accept(result);
        };
    }

    public static com.esaulpaugh.headlong.abi.Address asHeadlongAddress(final byte[] address) {
        final var addressBytes = org.apache.tuweni.bytes.Bytes.wrap(address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(addressAsInteger));
    }

    public static org.apache.tuweni.bytes.Bytes bytesForRedirect(
            final ByteBuffer encodedErcCall, final TokenID tokenId) {
        return bytesForRedirect(encodedErcCall.array(), asLongZeroAddress(tokenId.tokenNum()));
    }

    public static org.apache.tuweni.bytes.Bytes bytesForRedirect(
            final byte[] subSelector, final org.hyperledger.besu.datatypes.Address tokenAddress) {
        return org.apache.tuweni.bytes.Bytes.concatenate(
                org.apache.tuweni.bytes.Bytes.wrap(HtsCallAttempt.REDIRECT_FOR_TOKEN.selector()),
                tokenAddress,
                org.apache.tuweni.bytes.Bytes.of(subSelector));
    }
}
