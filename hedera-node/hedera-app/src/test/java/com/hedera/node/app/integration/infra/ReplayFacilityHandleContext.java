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

package com.hedera.node.app.integration.infra;

import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.facilities.ReplayIds;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ReplayFacilityHandleContext implements HandleContext {
    private final ReplayIds replayIds;
    private final ExpiryValidator expiryValidator;
    private final AttributeValidator attributeValidator;
    private final ReplayAdvancingConsensusNow consensusNow;

    @Inject
    public ReplayFacilityHandleContext(
            @NonNull final ReplayIds replayIds,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ReplayAdvancingConsensusNow consensusNow) {
        this.replayIds = replayIds;
        this.consensusNow = consensusNow;
        this.expiryValidator = expiryValidator;
        this.attributeValidator = attributeValidator;
    }

    @Override
    public Instant consensusNow() {
        return consensusNow.get();
    }

    @Override
    public LongSupplier newEntityNumSupplier() {
        return replayIds;
    }

    @Override
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    @Override
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }
}
