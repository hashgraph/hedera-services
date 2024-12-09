/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule.SignScheduleTranslator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Provides the {@link CallTranslator} implementations for the HSS system contract.
 */
@Module
public interface HssTranslatorsModule {
    @Provides
    @Singleton
    @Named("HssTranslators")
    static List<CallTranslator<HssCallAttempt>> provideCallAttemptTranslators(
            @NonNull @Named("HssTranslators") final Set<CallTranslator<HssCallAttempt>> translators) {
        return List.copyOf(translators);
    }

    @Provides
    @Singleton
    @IntoSet
    @Named("HssTranslators")
    static CallTranslator<HssCallAttempt> provideSignScheduleTranslator(
            @NonNull final SignScheduleTranslator translator) {
        return translator;
    }
}
