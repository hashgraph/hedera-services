/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package contract;

import com.hedera.hapi.node.base.AccountID;

public class MiscClassicTransfersXTestConstants {
    static final int INITIAL_RECEIVER_AUTO_ASSOCIATIONS = 123;
    static final long INITIAL_OWNER_FUNGIBLE_BALANCE = 1000L;
    static final long NEXT_ENTITY_NUM = 8080808080808L;
    static final AccountID LAZY_CREATED_ID =
            AccountID.newBuilder().accountNum(NEXT_ENTITY_NUM).build();
}
