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

package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.util.function.BooleanSupplier;

@Module
public interface ProcessLogicModule {
    @Provides
    @Singleton
    static BooleanSupplier provideFacilityRecordingTest() {
        return MiscUtils::isRecordingFacilityMocks;
    }

    @Provides
    @Singleton
    static ProcessLogic provideProcessLogic(
            @NonNull final StandardProcessLogic standardProcessLogic,
            @NonNull final BooleanSupplier isRecordingFacilityMocks) {
        if (isRecordingFacilityMocks.getAsBoolean()) {
            return new TxnRecordingProcessLogic(standardProcessLogic);
        } else {
            return standardProcessLogic;
        }
    }
}
