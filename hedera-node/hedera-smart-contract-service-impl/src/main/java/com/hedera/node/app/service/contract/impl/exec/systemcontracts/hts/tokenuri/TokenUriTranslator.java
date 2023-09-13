package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;

@Singleton
public class TokenUriTranslator extends AbstractHtsCallTranslator {
    public static final Function TOKEN_URI = new Function("tokenURI(uint256)", ReturnTypes.STRING);

    @Inject
    public TokenUriTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), TOKEN_URI.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var serialNo = asExactLongValueOrZero(
                TokenUriTranslator.TOKEN_URI.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new TokenUriCall(attempt.enhancement(), attempt.redirectToken(), serialNo);
    }
}
