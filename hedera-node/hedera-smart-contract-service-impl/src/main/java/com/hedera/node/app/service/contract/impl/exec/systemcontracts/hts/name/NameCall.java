package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Arrays;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static java.util.Objects.requireNonNull;

/**
 * Implements the token redirect {@code name()} call of the HTS system contract.
 */
public class NameCall extends AbstractHtsCall {
    public static final Function NAME = new Function("name()", ReturnTypes.STRING);

    @Nullable
    private final Token token;

    public NameCall(@NonNull final HederaWorldUpdater.Enhancement enhancement, @Nullable final Token token) {
        super(enhancement);
        this.token = token;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // TODO - gas calculation
        if (token == null) {
            return gasOnly(revertResult(INVALID_TOKEN_ID, 0L));
        }
        final var output = NAME.getOutputs().encodeElements(token.name());
        return gasOnly(successResult(output, 0L));
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link NameCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link NameCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, NAME.selector());
    }

    /**
     * Constructs a {@link NameCall} from the given {@code attempt}.
     *
     * @param attempt the attempt to construct from
     * @return the constructed {@link NameCall}
     */
    public static NameCall from(@NonNull final HtsCallAttempt attempt) {
        return new NameCall(attempt.enhancement(), attempt.redirectToken());
    }
}
