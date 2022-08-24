/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertiesModule;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.expiry.removal.AccountGC;
import com.hedera.services.state.expiry.removal.ContractGC;
import com.hedera.services.state.expiry.removal.RemovalHelper;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.state.expiry.renewal.RenewalHelper;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.state.expiry.renewal.RenewalWork;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module(includes = {PropertiesModule.class})
public interface ExpiryModule {
    @Provides
    @Singleton
    static RenewalWork providesRenewalWork(
            final EntityLookup lookup,
            final ClassificationWork classifier,
            final GlobalDynamicProperties properties,
            final FeeCalculator fees,
            final RenewalRecordsHelper recordsHelper) {
        return new RenewalHelper(lookup, classifier, properties, fees, recordsHelper);
    }

    @Provides
    @Singleton
    static RemovalWork providesRemovalWork(
            final ClassificationWork classifier,
            final GlobalDynamicProperties properties,
            final ContractGC contractGC,
            final AccountGC accountGC,
            final RenewalRecordsHelper recordsHelper) {
        return new RemovalHelper(classifier, properties, contractGC, accountGC, recordsHelper);
    }
}
