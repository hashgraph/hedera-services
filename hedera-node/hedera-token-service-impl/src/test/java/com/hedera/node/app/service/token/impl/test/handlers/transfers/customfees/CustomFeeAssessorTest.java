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

package com.hedera.node.app.service.token.impl.test.handlers.transfers.customfees;

import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomRoyaltyFeeAssessor;
import com.hedera.node.app.service.token.impl.test.handlers.transfers.StepsBase;
import com.hedera.node.app.spi.workflows.HandleContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeAssessorTest extends StepsBase {
    @Mock
    private HandleContext handleContext;

    private final TransferContextImpl transferContext = new TransferContextImpl(handleContext);
    private final CustomFixedFeeAssessor fixedFeeAssessor = new CustomFixedFeeAssessor();
    private final CustomFractionalFeeAssessor fractionalFeeAssessor = new CustomFractionalFeeAssessor(fixedFeeAssessor);
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor =
            new CustomRoyaltyFeeAssessor(fixedFeeAssessor, transferContext);
}
