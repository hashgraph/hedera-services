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

package com.hedera.node.app.service.file.impl.config;

import com.hedera.node.app.spi.config.PropertyNames;
import com.swirlds.config.api.ConfigProperty;

public record FileServiceConfig(
        @ConfigProperty(PropertyNames.FILES_MAX_NUM) long maxNum,
        @ConfigProperty(PropertyNames.FILES_MAX_SIZE_KB) int maxSizeKB,
        @ConfigProperty(PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION) long maxAutoRenewDuration,
        @ConfigProperty(PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION) long minAutoRenewDuration) {}
