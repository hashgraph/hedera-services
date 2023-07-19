package com.hedera.node.app.service.contract.impl.test.exec.scope;

import com.hedera.node.app.service.contract.impl.exec.scope.HandleContextScope;
import com.hedera.node.app.spi.workflows.HandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleContextScopeTest {
    @Mock
    private HandleContext context;

    private HandleContextScope subject;

    @BeforeEach
    void setUp() {
        subject = new HandleContextScope(context);
    }


}