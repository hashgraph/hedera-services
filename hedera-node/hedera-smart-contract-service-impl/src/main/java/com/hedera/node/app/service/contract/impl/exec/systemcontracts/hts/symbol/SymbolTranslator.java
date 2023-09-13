package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;


@Singleton
public class SymbolTranslator extends AbstractHtsCallTranslator {
    public static final Function SYMBOL = new Function("symbol()", ReturnTypes.STRING);

    @Inject
    public SymbolTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), SYMBOL.selector());
    }

    @Override
    public SymbolCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new SymbolCall(attempt.enhancement(), attempt.redirectToken());
    }
}
