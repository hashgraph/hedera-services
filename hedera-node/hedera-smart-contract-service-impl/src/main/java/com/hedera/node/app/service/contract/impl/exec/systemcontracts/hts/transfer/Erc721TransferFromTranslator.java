package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.Arrays;

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.Objects.requireNonNull;

@Singleton
public class Erc721TransferFromTranslator extends AbstractHtsCallTranslator {

    public static final Function ERC_721_TRANSFER_FROM = new Function("transferFrom(address,address,uint256)");

    @Inject
    public Erc721TransferFromTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        // We only match calls to existing tokens (i.e., with known token type)
        return attempt.isTokenRedirect()
                && Arrays.equals(attempt.selector(), Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM.selector())
                && attempt.redirectTokenType() == NON_FUNGIBLE_UNIQUE;
    }

    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var call = Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM.decodeCall(attempt.input().toArrayUnsafe());
        return new Erc721TransferFromCall(
                ((BigInteger) call.get(2)).longValueExact(),
                call.get(0),
                call.get(1),
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                attempt.verificationStrategies()
                        .activatingOnlyContractKeysFor(
                                attempt.senderAddress(),
                                attempt.onlyDelegatableContractKeysActive(),
                                attempt.enhancement().nativeOperations()),
                attempt.senderAddress(),
                attempt.enhancement(),
                attempt.addressIdConverter());
    }
}
