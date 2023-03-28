/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config.legacy;

import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bean for all parameters that can be part of the config.txt file
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future once the
 * 		config.txt has been migrated to the regular config API. If you need to use this class please try to do as less
 * 		static access as possible.
 */
@Deprecated(forRemoval = true)
public class LegacyConfigProperties {

    private JarAppConfig appConfig = null;

    private final List<AddressConfig> addressConfigs = new ArrayList<>();

    public void addAddressConfig(final AddressConfig addressConfig) {
        addressConfigs.add(addressConfig);
    }

    public List<AddressConfig> getAddressConfigs() {
        return Collections.unmodifiableList(addressConfigs);
    }

    public void setAppConfig(final JarAppConfig appConfig) {
        this.appConfig = CommonUtils.throwArgNull(appConfig, "appConfig");
    }

    public Optional<JarAppConfig> appConfig() {
        return Optional.ofNullable(appConfig);
    }
}
