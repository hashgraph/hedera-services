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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;

/**
 * Represents the result of attempting to expand a transaction's signature list.
 *
 * @param cryptoSigs the expanded list of crypto signatures
 * @param status the status of the expansion attempt
 */
public record SigExpansionResult(List<TransactionSignature> cryptoSigs, ResponseCodeEnum status) {}
