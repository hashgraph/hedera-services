package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameCall;
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
 * Implements the token redirect {@code symbol()} call of the HTS system contract.
 */
public class SymbolCall extends AbstractHtsCall {
    public static final Function SYMBOL = new Function("symbol()", ReturnTypes.STRING);

    @Nullable
    private final Token token;

    public SymbolCall(@NonNull final HederaWorldUpdater.Enhancement enhancement, @Nullable final Token token) {
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
        final var output = SYMBOL.getOutputs().encodeElements(token.symbol());
        return gasOnly(successResult(output, 0L));
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link SymbolCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link SymbolCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, SYMBOL.selector());
    }

    /**
     * Constructs a {@link SymbolCall} from the given {@code attempt}.
     *
     * @param attempt the attempt to construct from
     * @return the constructed {@link SymbolCall}
     */
    public static SymbolCall from(@NonNull final HtsCallAttempt attempt) {
        return new SymbolCall(attempt.enhancement(), attempt.redirectToken());
    }
}
