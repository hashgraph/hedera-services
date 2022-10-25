package com.hedera.node.app.service.token.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerNotImplTest {
    @Mock private AccountStore store;

    private CryptoPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new CryptoPreTransactionHandlerImpl(store);
    }

    @Test
    void name() {

    }
}