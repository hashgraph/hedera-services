package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name;

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

import static java.util.Objects.requireNonNull;

@Singleton
public class NameTranslator extends AbstractHtsCallTranslator {
    public static final Function NAME = new Function("name()", ReturnTypes.STRING);

    @Inject
    public NameTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), NAME.selector());
    }

    @Override
    public NameCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new NameCall(attempt.enhancement(), attempt.redirectToken());
    }
}
