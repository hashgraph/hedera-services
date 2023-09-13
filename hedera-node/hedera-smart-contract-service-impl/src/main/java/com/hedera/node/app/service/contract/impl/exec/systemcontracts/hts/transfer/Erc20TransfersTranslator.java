package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.math.BigInteger;
import java.util.Arrays;

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.Objects.requireNonNull;

@Singleton
public class Erc20TransfersTranslator extends AbstractHtsCallTranslator {
    public static final Function ERC_20_TRANSFER = new Function("transfer(address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_20_TRANSFER_FROM =
            new Function("transferFrom(address,address,uint256)", ReturnTypes.BOOL);

    @Inject
    public Erc20TransfersTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        // We will match the transferFrom() selector shared by ERC-20 and ERC-721 if the token is missing
        return attempt.isTokenRedirect()
                && selectorsInclude(attempt.selector())
                && attempt.redirectTokenType() != NON_FUNGIBLE_UNIQUE;
    }

    @Override
    public @Nullable HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        if (isErc20Transfer(attempt.selector())) {
            final var call = Erc20TransfersTranslator.ERC_20_TRANSFER.decodeCall(attempt.input().toArrayUnsafe());
            return callFrom(attempt.senderAddress(), attempt.onlyDelegatableContractKeysActive(), null, call.get(0), call.get(1), attempt);
        } else {
            final var call = Erc20TransfersTranslator.ERC_20_TRANSFER_FROM.decodeCall(attempt.input().toArrayUnsafe());
            return callFrom(attempt.senderAddress(), attempt.onlyDelegatableContractKeysActive(), call.get(0), call.get(1), call.get(2), attempt);
        }
    }

    private Erc20TransfersCall callFrom(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean senderNeedsDelegatableContractKeys,
            @Nullable final Address from,
            @NonNull final Address to,
            @NonNull final BigInteger amount,
            @NonNull final HtsCallAttempt attempt) {
        return new Erc20TransfersCall(
                attempt.enhancement(),
                amount.longValueExact(),
                from,
                to,
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                attempt.verificationStrategies()
                        .activatingOnlyContractKeysFor(
                                sender,
                                senderNeedsDelegatableContractKeys,
                                attempt.enhancement().nativeOperations()),
                sender,
                attempt.addressIdConverter());
    }

    private boolean selectorsInclude(@NonNull final byte[] selector) {
        return isErc20Transfer(selector) || isErc20TransferFrom(selector);
    }

    private boolean isErc20Transfer(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_20_TRANSFER.selector());
    }

    private boolean isErc20TransferFrom(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_20_TRANSFER_FROM.selector());
    }
}
