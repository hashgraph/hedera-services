/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import com.hedera.services.files.sysfiles.ConfigCallbacks;
import com.hedera.services.files.sysfiles.CurrencyCallbacks;
import com.hedera.services.files.sysfiles.ThrottlesCallback;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SysFileCallbacks {
    private final ConfigCallbacks configCallbacks;
    private final ThrottlesCallback throttlesCallback;
    private final CurrencyCallbacks currencyCallbacks;

    @Inject
    public SysFileCallbacks(
            ConfigCallbacks configCallbacks,
            ThrottlesCallback throttlesCallback,
            CurrencyCallbacks currencyCallbacks) {
        this.configCallbacks = configCallbacks;
        this.throttlesCallback = throttlesCallback;
        this.currencyCallbacks = currencyCallbacks;
    }

    public Consumer<ExchangeRateSet> exchangeRatesCb() {
        return currencyCallbacks.exchangeRatesCb();
    }

    public Consumer<CurrentAndNextFeeSchedule> feeSchedulesCb() {
        return currencyCallbacks.feeSchedulesCb();
    }

    public Consumer<ThrottleDefinitions> throttlesCb() {
        return throttlesCallback.throttlesCb();
    }

    public Consumer<ServicesConfigurationList> propertiesCb() {
        return configCallbacks.propertiesCb();
    }

    public Consumer<ServicesConfigurationList> permissionsCb() {
        return configCallbacks.permissionsCb();
    }
}
