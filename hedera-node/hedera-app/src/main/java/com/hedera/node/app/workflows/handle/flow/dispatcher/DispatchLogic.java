package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;

@Singleton
public class DispatchLogic {

    @Inject
    public DispatchLogic() {
    }

    public void dispatch(HandleContext handleContext,
                         Function<HandleContext, Fees> feeCalculator,
                         ResponseCodeEnum pureChecksResult) {
        final var fees = feeCalculator.apply(handleContext);

    }
}
