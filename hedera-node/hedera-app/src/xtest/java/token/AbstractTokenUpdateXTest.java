package token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Consumer;

public abstract class AbstractTokenUpdateXTest extends AbstractXTest {
    protected static final AccountID DEFAULT_PAYER_ID =
            AccountID.newBuilder().accountNum(2L).build();
    protected TokenScaffoldingComponent component;

    @BeforeEach
    void setUp() {
        component = DaggerTokenScaffoldingComponent.factory().create(metrics, configuration());
    }

    protected Configuration configuration() {
        return HederaTestConfigBuilder.create().getOrCreateConfig();
    }

    @Override
    protected BaseScaffoldingComponent component() {
        return component;
    }

    protected Consumer<TokenUpdateTransactionBody.Builder> sas() {
        return null;
    }
}
