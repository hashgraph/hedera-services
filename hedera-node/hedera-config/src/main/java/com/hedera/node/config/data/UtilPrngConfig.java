// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.config.data;

import com.hedera.node.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("utilPrng")
public record UtilPrngConfig(@ConfigProperty(defaultValue = "true") @NetworkProperty boolean isEnabled) {}
