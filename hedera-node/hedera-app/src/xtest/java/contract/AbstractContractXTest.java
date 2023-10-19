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

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static contract.XTestConstants.PLACEHOLDER_CALL_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAddressChecks;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SyntheticIds;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

/**
 * Base class for {@code xtest} scenarios that focus on contract operations.
 */
public abstract class AbstractContractXTest extends AbstractXTest {
    private static final SyntheticIds LIVE_SYNTHETIC_IDS = new SyntheticIds();
    private static final VerificationStrategies LIVE_VERIFICATION_STRATEGIES = new VerificationStrategies();
    static final long GAS_TO_OFFER = 2_000_000L;
    static final Duration STANDARD_AUTO_RENEW_PERIOD = new Duration(7776000L);

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame initialFrame;

    @Mock
    private ProxyWorldUpdater proxyUpdater;

    @Mock
    private HtsCallAddressChecks addressChecks;

    private HtsCallFactory callAttemptFactory;

    private ContractScaffoldingComponent component;

    @BeforeEach
    void setUp() {
        component = DaggerContractScaffoldingComponent.factory().create(metrics, configuration());
        callAttemptFactory = new HtsCallFactory(
                LIVE_SYNTHETIC_IDS, addressChecks, LIVE_VERIFICATION_STRATEGIES, component.callTranslators());
    }

    protected Configuration configuration() {
        return HederaTestConfigBuilder.create()
                .withValue("contracts.chainId", "298")
                .getOrCreateConfig();
    }

    @Override
    protected BaseScaffoldingComponent component() {
        return component;
    }

    protected void handleAndCommit(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = component.txnContextFactory().apply(txn);
            handler.handle(context);
            ((SavepointStackImpl) context.savepointStack()).commitFullStack();
        }
    }

    protected void runHtsCallAndExpectOnSuccess(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions) {
        runHtsCallAndExpectOnSuccess(false, sender, input, outputAssertions, null);
    }

    protected void runHtsCallAndExpectOnSuccess(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions,
            @NonNull final String context) {
        runHtsCallAndExpectOnSuccess(false, sender, input, outputAssertions, context);
    }

    protected void runDelegatedHtsCallAndExpectOnSuccess(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions) {
        runHtsCallAndExpectOnSuccess(true, sender, input, outputAssertions, null);
    }

    private void runHtsCallAndExpectOnSuccess(
            final boolean requiresDelegatePermission,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final Consumer<org.apache.tuweni.bytes.Bytes> outputAssertions,
            @Nullable final String context) {
        runHtsCallAndExpect(requiresDelegatePermission, sender, input, resultOnlyAssertion(result -> {
            assertEquals(
                    MessageFrame.State.COMPLETED_SUCCESS,
                    result.getState(),
                    Optional.ofNullable(context).orElse("An unspecified operation") + " should have succeeded");
            outputAssertions.accept(result.getOutput());
        }));
    }

    protected void runHtsCallAndExpectRevert(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final ResponseCodeEnum status) {
        internalRunHtsCallAndExpectRevert(sender, input, status, null);
    }

    protected void runHtsCallAndExpectRevert(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final ResponseCodeEnum status,
            @NonNull final String context) {
        internalRunHtsCallAndExpectRevert(sender, input, status, context);
    }

    private void internalRunHtsCallAndExpectRevert(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final org.apache.tuweni.bytes.Bytes input,
            @NonNull final ResponseCodeEnum status,
            @Nullable final String context) {
        runHtsCallAndExpect(false, sender, input, resultOnlyAssertion(result -> {
            assertEquals(
                    MessageFrame.State.REVERT,
                    result.getState(),
                    Optional.ofNullable(context).orElse("An unspecified operation") + " should have reverted");
            final var actualReason =
                    ResponseCodeEnum.fromString(new String(result.getOutput().toArrayUnsafe()));
            assertEquals(status, actualReason);
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
        final var context = component.txnContextFactory().apply(PLACEHOLDER_CALL_BODY);
        final var tinybarValues = new TinybarValues(
                context.exchangeRateInfo().activeRate(Instant.now()),
                context.resourcePricesFor(HederaFunctionality.CONTRACT_CALL, SubType.DEFAULT));
        final var enhancement = new HederaWorldUpdater.Enhancement(
                new HandleHederaOperations(
                        component.config().getConfigData(LedgerConfig.class),
                        component.config().getConfigData(ContractsConfig.class),
                        context,
                        tinybarValues),
                new HandleHederaNativeOperations(context),
                new HandleSystemContractOperations(context));
        given(proxyUpdater.enhancement()).willReturn(enhancement);
        given(frame.getWorldUpdater()).willReturn(proxyUpdater);
        given(frame.getSenderAddress()).willReturn(sender);
        final Deque<MessageFrame> stack = new ArrayDeque<>();
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(component.config());
        stack.push(initialFrame);
        stack.addFirst(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(addressChecks.hasParentDelegateCall(frame)).willReturn(requiresDelegatePermission);

        final var call = callAttemptFactory.createCallFrom(input, frame);

        final var pricedResult = call.execute();
        resultAssertions.accept(pricedResult);
        // Note that committing a reverted calls should have no effect on state
        ((SavepointStackImpl) context.savepointStack()).commitFullStack();
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

    protected Address addressOf(@NonNull final Bytes address) {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(1, address.toByteArray())));
    }

    protected Consumer<Response> assertingCallLocalResultIs(@NonNull final Bytes expectedResult) {
        return response -> assertEquals(
                expectedResult,
                response.contractCallLocalOrThrow().functionResultOrThrow().contractCallResult());
    }

    protected Consumer<Response> assertingCallLocalResultIs(@NonNull final ByteBuffer expectedResult) {
        return response -> assertThat(expectedResult.array())
                .isEqualTo(response.contractCallLocalOrThrow()
                        .functionResultOrThrow()
                        .contractCallResult()
                        .toByteArray());
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

    public static org.apache.tuweni.bytes.Bytes asBytesResult(final ByteBuffer encoded) {
        return org.apache.tuweni.bytes.Bytes.wrap(encoded.array());
    }
}
