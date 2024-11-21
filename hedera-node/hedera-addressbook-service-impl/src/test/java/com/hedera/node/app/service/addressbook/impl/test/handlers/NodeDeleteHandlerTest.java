/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.addressbook.NodeDeleteTransactionBody;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeDeleteHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeDeleteHandlerTest extends AddressBookTestBase {

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableNodeStoreImpl mockStore;

    @Mock(strictness = Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock
    private NodeDeleteHandler subject;

    @Mock
    private StoreMetricsService storeMetricsService;

    protected Configuration testConfig;

    @BeforeEach
    void setUp() {
        mockStore = mock(ReadableNodeStoreImpl.class);
        subject = new NodeDeleteHandler();

        writableNodeState = writableNodeStateWithOneKey();
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        testConfig = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, testConfig, storeMetricsService);
        lenient().when(handleContext.configuration()).thenReturn(testConfig);
    }

    @Test
    @DisplayName("pureChecks throws exception when node id is negative or zero")
    void testPureChecksThrowsExceptionWhenFileIdIsNull() {
        NodeDeleteTransactionBody transactionBody = mock(NodeDeleteTransactionBody.class);
        TransactionBody transaction = mock(TransactionBody.class);
        given(handleContext.body()).willReturn(transaction);
        given(transaction.nodeDeleteOrThrow()).willReturn(transactionBody);
        given(transactionBody.nodeId()).willReturn(-1L);

        assertThatThrownBy(() -> subject.pureChecks(handleContext.body())).isInstanceOf(PreCheckException.class);
        var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(handleContext.body()));
        assertThat(msg.responseCode()).isEqualTo(INVALID_NODE_ID);
    }

    @Test
    @DisplayName("pureChecks does not throw exception when node id is not null")
    void testPureChecksDoesNotThrowExceptionWhenNodeIdIsNotNull() {
        given(handleContext.body()).willReturn(newDeleteTxn());

        assertThatCode(() -> subject.pureChecks(handleContext.body())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("check that fees are 1 for delete node trx")
    void testCalculateFeesInvocations() throws IOException {
        final var feeCtx = mock(FeeContext.class);
        final var feeCalcFact = mock(FeeCalculatorFactory.class);
        final var feeCalc = mock(FeeCalculator.class);
        given(feeCtx.feeCalculatorFactory()).willReturn(feeCalcFact);
        given(feeCalcFact.feeCalculator(any())).willReturn(feeCalc);
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.enableDAB", true)
                .getOrCreateConfig();
        given(feeCtx.configuration()).willReturn(config);

        given(feeCalc.addVerificationsPerTransaction(anyLong())).willReturn(feeCalc);
        given(feeCalc.calculate()).willReturn(new Fees(1, 0, 0));

        assertThat(subject.calculateFees(feeCtx)).isEqualTo(new Fees(1, 0, 0));
    }

    @Test
    @DisplayName("Fails handle if node doesn't exist")
    void fileDoesntExist() {
        final var txn = newDeleteTxn().nodeDeleteOrThrow();

        given(handleContext.storeFactory()).willReturn(storeFactory);
        writableNodeState = emptyWritableNodeState();
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        writableStore = new WritableNodeStore(writableStates, testConfig, storeMetricsService);
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().nodeDelete(txn).build());
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        HandleException thrown = (HandleException) catchThrowable(() -> subject.handle(handleContext));
        assertThat(thrown.getStatus()).isEqualTo(INVALID_NODE_ID);
    }

    @Test
    @DisplayName("Node is null")
    void NodeIsNull() {
        final var txn = newDeleteTxn().nodeDeleteOrThrow();

        node = null;

        given(handleContext.storeFactory()).willReturn(storeFactory);
        writableNodeState = writableNodeStateWithOneKey();
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        writableStore = new WritableNodeStore(writableStates, testConfig, storeMetricsService);
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().nodeDelete(txn).build());
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);
        HandleException thrown = (HandleException) catchThrowable(() -> subject.handle(handleContext));
        assertThat(thrown.getStatus()).isEqualTo(INVALID_NODE_ID);
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        final var txn = newDeleteTxn().nodeDeleteOrThrow();

        final var existingNode = writableStore.get(WELL_KNOWN_NODE_ID);
        assertThat(existingNode).isNotNull();
        assertThat(existingNode.deleted()).isFalse();

        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().nodeDelete(txn).build());
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        subject.handle(handleContext);

        final var changedFile = writableStore.get(WELL_KNOWN_NODE_ID);

        assertThat(changedFile).isNotNull();
        assertThat(changedFile.deleted()).isTrue();
    }

    @Test
    @DisplayName("Node already deleted returns error")
    void noFileKeys() {
        givenValidNode(true);
        refreshStoresWithCurrentNodeInBothReadableAndWritable();

        final var txn = newDeleteTxn().nodeDeleteOrThrow();

        final var existingNode = writableStore.get(WELL_KNOWN_NODE_ID);
        assertThat(existingNode).isNotNull();
        assertThat(existingNode.deleted()).isTrue();

        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().nodeDelete(txn).build());
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableNodeStore.class)).willReturn(writableStore);
        // expect:
        assertFailsWith(() -> subject.handle(handleContext), ResponseCodeEnum.NODE_DELETED);
    }

    @Test
    void preHandleDoesNothing() {
        assertDoesNotThrow(() -> subject.preHandle(mock(PreHandleContext.class)));
    }

    private TransactionBody newDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileBuilder = NodeDeleteTransactionBody.newBuilder().nodeId(WELL_KNOWN_NODE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .nodeDelete(deleteFileBuilder.build())
                .build();
    }

    private static void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
        assertThatThrownBy(something::run)
                .isInstanceOf(HandleException.class)
                .extracting(ex -> ((HandleException) ex).getStatus())
                .isEqualTo(status);
    }
}
