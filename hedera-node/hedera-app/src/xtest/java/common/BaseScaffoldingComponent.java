package common;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.swirlds.config.api.Configuration;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface BaseScaffoldingComponent {
    HederaState hederaState();

    Configuration config();

    WorkingStateAccessor workingStateAccessor();

    Function<TransactionBody, HandleContext> txnContextFactory();

    BiFunction<Query, AccountID, QueryContext> queryContextFactory();

    FeeManager feeManager();

    ExchangeRateManager exchangeRateManager();
}
