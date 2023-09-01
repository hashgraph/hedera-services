package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNftViewCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;
import java.util.Arrays;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static java.util.Objects.requireNonNull;

/**
 * Implements the token redirect {@code ownerOf()} call of the HTS system contract.
 */
public class OwnerOfCall extends AbstractNftViewCall {
    private static final long TREASURY_OWNER_NUM = 0L;

    public static final Function OWNER_OF = new Function("ownerOf(uint256)", ReturnTypes.ADDRESS);

    public OwnerOfCall(
            @NonNull HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(enhancement, token, serialNo);
    }

    @Override
    protected HederaSystemContract.FullResult resultOfViewingNft(@NonNull final Token token, @NonNull final Nft nft) {
        requireNonNull(token);
        requireNonNull(nft);

        // TODO - gas calculation
        final var explicitId = nft.ownerIdOrElse(AccountID.DEFAULT);
        final long ownerNum;
        if (explicitId.accountNumOrElse(TREASURY_OWNER_NUM) == TREASURY_OWNER_NUM) {
            ownerNum = token.treasuryAccountIdOrThrow().accountNumOrThrow();
        } else {
            ownerNum = explicitId.accountNumOrThrow();
        }
        final var owner = nativeOperations().getAccount(ownerNum);
        if (owner == null) {
            return revertResult(INVALID_ACCOUNT_ID, 0L);
        } else {
            final var output = OWNER_OF.getOutputs().encodeElements(headlongAddressOf(owner));
            return HederaSystemContract.FullResult.successResult(output, 0L);
        }
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link OwnerOfCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link OwnerOfCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, OWNER_OF.selector());
    }

    /**
     * Constructs a {@link OwnerOfCall} from the given {@code attempt}.
     *
     * @param attempt the attempt to construct from
     * @return the constructed {@link OwnerOfCall}
     */
    public static OwnerOfCall from(@NonNull final HtsCallAttempt attempt) {
        // Since zero is never a valid serial number, if we clamp the passed value, the result
        // will be a revert with INVALID_NFT_ID as reason
        final var serialNo = asExactLongValueOrZero(OWNER_OF.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new OwnerOfCall(attempt.enhancement(), attempt.redirectToken(), serialNo);
    }
}
