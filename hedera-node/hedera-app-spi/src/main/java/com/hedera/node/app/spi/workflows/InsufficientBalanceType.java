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

package com.hedera.node.app.spi.workflows;

/**
 * Gives the type of insufficient balance (or willingness-to-pay) encountered during a solvency check.
 */
public enum InsufficientBalanceType {
    /**
     * The payer's account balance or willingness-to-pay did not cover the network fee (this is
     * a due diligence failure).
     */
    NETWORK_FEE_NOT_COVERED,
    /**
     * The payer's account balance or willingness-to-pay did to cover the service fee (this is
     * not a due diligence failure, but implies we will not charge any part of the service fee).
     */
    SERVICE_FEES_NOT_COVERED,
    /**
     * The payer's account balance could not cover additional charges implied by the transaction (this
     * will not waive any part of the service fee).
     */
    OTHER_COSTS_NOT_COVERED,
}
