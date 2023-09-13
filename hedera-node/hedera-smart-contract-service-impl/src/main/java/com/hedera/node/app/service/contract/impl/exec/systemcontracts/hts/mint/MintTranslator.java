package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

@Singleton
public class MintTranslator extends AbstractHtsCallTranslator {
    public static final Function MINT = new Function("mintToken(address,uint64,bytes[])", ReturnTypes.INT);
    public static final Function MINT_V2 = new Function("mintToken(address,int64,bytes[])", ReturnTypes.INT);

    @Inject
    public MintTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), MintTranslator.MINT.selector()) ||
                Arrays.equals(attempt.selector(), MintTranslator.MINT_V2.selector());
    }

    @Override
    public @Nullable MintCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        if (Arrays.equals(selector, MintTranslator.MINT.selector())) {
            call = MintTranslator.MINT.decodeCall(attempt.input().toArrayUnsafe());
        } else {
            call = MintTranslator.MINT_V2.decodeCall(attempt.input().toArrayUnsafe());
        }
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return token.tokenType() == TokenType.FUNGIBLE_COMMON
                    ? new FungibleMintCall(attempt.enhancement())
                    : new NonFungibleMintCall(attempt.enhancement());
        }
    }
}
