package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class TokenServiceImplTest {
    @Mock
    private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private InMemoryStateImpl tokens;
    @Mock
    States states;
    @Mock
    PreHandleContext ctx;

    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";
    private static final String TOKENS = "TOKENS";
    private TokenServiceImpl subject;

    @Test
    void createsNewInstance() {
        subject = new TokenServiceImpl();

        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        given(states.get(TOKENS)).willReturn(tokens);

        final var serviceImpl = subject.createPreTransactionHandler(states, ctx);
        final var serviceImpl1 = subject.createPreTransactionHandler(states, ctx);
        assertNotEquals(serviceImpl1, serviceImpl);
    }
}
