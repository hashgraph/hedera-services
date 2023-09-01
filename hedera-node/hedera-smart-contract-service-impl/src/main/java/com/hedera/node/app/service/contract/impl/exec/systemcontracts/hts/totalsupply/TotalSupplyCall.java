package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;
import java.util.Arrays;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static java.util.Objects.requireNonNull;

/**
 * Implements the token redirect {@code totalSupply()} call of the HTS system contract.
 */
public class TotalSupplyCall extends AbstractHtsCall {
    public static final Function TOTAL_SUPPLY = new Function("totalSupply()", ReturnTypes.INT);

    @Nullable
    private final Token token;

    public TotalSupplyCall(@NonNull final HederaWorldUpdater.Enhancement enhancement, @Nullable final Token token) {
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
        final var output = TOTAL_SUPPLY.getOutputs().encodeElements(BigInteger.valueOf(token.totalSupply()));
        return gasOnly(successResult(output, 0L));
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link TotalSupplyCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link TotalSupplyCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, TOTAL_SUPPLY.selector());
    }

    /**
     * Constructs a {@link TotalSupplyCall} from the given {@code attempt}.
     *
     * @param attempt the attempt to construct from
     * @return the constructed {@link TotalSupplyCall}
     */
    public static TotalSupplyCall from(@NonNull final HtsCallAttempt attempt) {
        return new TotalSupplyCall(attempt.enhancement(), attempt.redirectToken());
    }
}
