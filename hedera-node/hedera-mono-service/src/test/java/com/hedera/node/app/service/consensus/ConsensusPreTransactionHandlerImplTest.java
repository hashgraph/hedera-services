package com.hedera.node.app.service.consensus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.node.app.Utils;
import com.hedera.node.app.service.consensus.impl.ConsensusPreTransactionHandlerImpl;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.service.token.impl.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.services.utils.KeyUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusPreTransactionHandlerImplTest {
    private static final AccountID ACCOUNT_ID_3 = IdUtils.asAccount("0.0.3");

    @Mock
    private AccountStore accountStore;

    private ConsensusPreTransactionHandlerImpl subject;


    @BeforeEach
    void setUp() {
        subject = new ConsensusPreTransactionHandlerImpl(accountStore);
    }

    @Test
    void createAddsNonNullKeys() {

    }

    @Test
    void createOnlyRequiresPayerKey() {
        final var payerKey = mockPayerLookup();

        final var result = subject.preHandleCreateTopic(newCreateTxn(null, null));

        Assertions.assertEquals(ResponseCodeEnum.OK, result.status());
        Assertions.assertEquals(List.of(payerKey), result.getReqKeys());
    }

    @Test
    void createAddsSeparateAdminKey() {
        final var payerKey = Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
        given(accountStore.getKey(ACCOUNT_ID_3)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var adminKey = KeyUtils.B_COMPLEX_KEY;

        final var result = subject.preHandleCreateTopic(newCreateTxn(adminKey, null));

        Assertions.assertTrue(result.getReqKeys().contains(payerKey));
        Assertions.assertTrue(result.getReqKeys().contains(Utils.asHederaKey(adminKey).get()));
    }

    @Test
    void createAddsSeparateSubmitKey() {

    }

    @Test
    void createAddsPayerAsAdmin() {

    }

    @Test
    void createAddsPayerAsSubmitter() {

    }

    @Test
    void createFailsWhenPayerIsntFound() {
        final var inputTxn = newCreateTxn(null, null);

        final var result = subject.preHandleCreateTopic(inputTxn);

        Assertions.assertEquals(ResponseCodeEnum.FAIL_BALANCE, result.status());
        Assertions.assertTrue(result.getReqKeys().isEmpty());
    }

    @Test
    void notImplementedMethodsThrowException() {
        assertThrows(NotImplementedException.class, () -> subject.preHandleUpdateTopic(
            mock(TransactionBody.class)));
        assertThrows(NotImplementedException.class, () -> subject.preHandleDeleteTopic(
            mock(TransactionBody.class)));
        assertThrows(NotImplementedException.class, () -> subject.preHandleSubmitMessage(
            mock(TransactionBody.class)));
    }

    private HederaKey mockPayerLookup() {
        final var returnKey = Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).orElseThrow();
        given(accountStore.getKey(ACCOUNT_ID_3)).willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }

    private static TransactionBody newCreateTxn(Key adminKey, Key submitKey) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3)
            .build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.setSubmitKey(submitKey);
        }
        return TransactionBody.newBuilder()
            .setTransactionID(txnId)
            .setConsensusCreateTopic(createTopicBuilder.build())
            .build();
    }
}
