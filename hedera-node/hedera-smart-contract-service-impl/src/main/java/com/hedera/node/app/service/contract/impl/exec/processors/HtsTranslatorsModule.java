package com.hedera.node.app.service.contract.impl.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Module
public interface HtsTranslatorsModule {

    @Provides
    @Singleton
    @IntoSet
    static Function<HtsCallAttempt, HtsCall> provideAssociationsTranslator(@NonNull final AssociationsTranslator translator) {
        return translator::translate;
    }

    @Provides
    @Singleton
    static List<Function<HtsCallAttempt, HtsCall>> provideCallAttemptTranslators(
            @NonNull final Set<Function<HtsCallAttempt, HtsCall>> translators) {
        return List.copyOf(translators);
    }
}
