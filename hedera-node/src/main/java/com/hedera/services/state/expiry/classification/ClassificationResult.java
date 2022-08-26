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
package com.hedera.services.state.expiry.classification;

public enum ClassificationResult {
    DETACHED_ACCOUNT,
    EXPIRED_ACCOUNT_READY_TO_RENEW,
    DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
    DETACHED_CONTRACT,
    EXPIRED_CONTRACT_READY_TO_RENEW,
    DETACHED_CONTRACT_GRACE_PERIOD_OVER,
    DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN,
    OTHER,
    COME_BACK_LATER
}
