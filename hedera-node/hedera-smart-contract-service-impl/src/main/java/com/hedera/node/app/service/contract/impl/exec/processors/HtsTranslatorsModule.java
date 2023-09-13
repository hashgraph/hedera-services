/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.contract.impl.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Singleton;

@Module
public interface HtsTranslatorsModule {

    @Provides
    @Singleton
    @IntoSet
    static Function<HtsCallAttempt, HtsCall> provideAssociationsTranslator(
            @NonNull final AssociationsTranslator translator) {
        return translator::translate;
    }

    @Provides
    @Singleton
    static List<Function<HtsCallAttempt, HtsCall>> provideCallAttemptTranslators(
            @NonNull final Set<Function<HtsCallAttempt, HtsCall>> translators) {
        return List.copyOf(translators);
    }
}
