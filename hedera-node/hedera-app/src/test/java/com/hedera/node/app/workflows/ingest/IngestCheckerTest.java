package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestCheckerTest {

    @Mock private TransitionLogicLookup transitionLogicLookup;
    @Mock private TransitionLogic transitionLogic;
    @Mock private GlobalDynamicProperties dynamicProperties;

    @Test
    void testDefaultCase() {
        // given
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody)).thenReturn(Optional.of(transitionLogic));
        when(transitionLogic.semanticCheck()).thenReturn(it -> OK);
        final IngestChecker checker = new IngestChecker(transitionLogicLookup, dynamicProperties);

        // then
        assertDoesNotThrow(() -> checker.checkTransactionSemantic(txBody, HederaFunctionality.NONE));
    }

    @Test
    void testUnsupportedFunctionality() {
        // given
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody)).thenReturn(Optional.empty());
        final IngestChecker checker = new IngestChecker(transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(() -> checker.checkTransactionSemantic(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", NOT_SUPPORTED);
    }

    @Test
    void testFailedSemanticCheck() {
        // given
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody)).thenReturn(Optional.of(transitionLogic));
        when(transitionLogic.semanticCheck()).thenReturn(it -> BATCH_SIZE_LIMIT_EXCEEDED);
        final IngestChecker checker = new IngestChecker(transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(() -> checker.checkTransactionSemantic(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", BATCH_SIZE_LIMIT_EXCEEDED);
    }

    @Disabled("Needs to be implemented")
    @Test
    void testTokenAccountWipe() {
        // TODO: Implement once this code path does not relay on static code
        fail("Test not implemented");
    }
}
