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

package com.hedera.node.app.service.contract.impl.hevm;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;

/**
 * TODO - not sure this makes sense, why can't we just call {@link Account#getCode()} directly?
 *
 * (Answer: we probably can. This class should be deleted in upcoming PR.)
 */
public interface HederaEvmCode {
    Code load(@NonNull Address contract);

    Code loadIfPresent(@NonNull Address contract);
}
