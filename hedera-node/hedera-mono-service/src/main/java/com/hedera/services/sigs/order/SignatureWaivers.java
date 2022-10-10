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
package com.hedera.services.sigs.order;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Defines a type able to decide if certain keys' signing requirements are waived for a given
 * transaction.
 */
public interface SignatureWaivers {
    /**
     * Advises if the target file's WACL must sign a given file append.
     *
     * @param fileAppendTxn a file append transaction
     * @return whether the target file's WACL must sign
     */
    boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn, final AccountID payer);

    /**
     * Advises if the target file's WACL must sign a given file update.
     *
     * @param fileUpdateTxn a file update transaction
     * @return whether the target file's WACL must sign
     */
    boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn, final AccountID payer);

    /**
     * Advises if the new WACL in a given file update transaction must sign.
     *
     * @param fileUpdateTxn a file update transaction
     * @return whether the new WACL from the transaction must sign
     */
    boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn, final AccountID payer);

    /**
     * Advises if the target account's key must sign a given crypto update.
     *
     * @param cryptoUpdateTxn a crypto update transaction
     * @return whether the target account's key must sign
     */
    boolean isTargetAccountKeyWaived(TransactionBody cryptoUpdateTxn, final AccountID payer);

    /**
     * Advises if the new key for an account must sign a given crypto update.
     *
     * @param cryptoUpdateTxn a crypto update transaction
     * @return whether the new key from the transaction must sign
     */
    boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn, final AccountID payer);
}
