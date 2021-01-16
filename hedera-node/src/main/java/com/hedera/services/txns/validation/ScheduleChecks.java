package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import org.apache.commons.codec.DecoderException;

import java.util.function.Predicate;

import static com.hedera.services.keys.KeysHelper.ed25519ToJKey;
import static com.hedera.services.txns.validation.PureValidation.checkKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KEY_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_SIG_MAP_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ScheduleChecks {
    static Predicate<Key> ADMIN_KEY_REMOVAL = ImmutableKeyUtils::signalsKeyRemoval;

    public static ResponseCodeEnum checkAdminKey(
            boolean hasAdminKey, Key adminKey
    ) {
        ResponseCodeEnum validity = OK;

        if (hasAdminKey && !ADMIN_KEY_REMOVAL.test(adminKey)) {
            if ((validity = checkKey(adminKey, INVALID_ADMIN_KEY)) != OK) {
                return validity;
            }
        }
        return validity;
    }

    public static ResponseCodeEnum validateSignatureMap(SignatureMap sigMap) {
        for (SignaturePair signaturePair : sigMap.getSigPairList()) {
            try {
                if (!ed25519ToJKey(signaturePair.getPubKeyPrefix()).isValid()) {
                    return INVALID_SCHEDULE_SIG_MAP_KEY;
                }
            }
            catch (DecoderException e) {
                return INVALID_KEY_ENCODING;
            }
        }
        return OK;
    }
}
