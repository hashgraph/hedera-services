package com.hedera.node.app.service.contract.impl.test.infra;

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.infra.HevmStaticTransactionFactory;
import com.hedera.node.app.service.token.ReadableAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class HevmStaticTransactionFactoryTest {
    @Mock
    private ReadableAccountStore accountStore;

    private HevmStaticTransactionFactory subject;

    @BeforeEach
    void setUp() {
        subject = new HevmStaticTransactionFactory(DEFAULT_CONTRACTS_CONFIG, accountStore);
    }

    @Test
    void fromHapiQueryNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiQuery(Query.DEFAULT));
    }
}