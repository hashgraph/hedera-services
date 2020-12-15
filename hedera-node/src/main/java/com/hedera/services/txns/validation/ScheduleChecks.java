package com.hedera.services.txns.validation;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.PureValidation.checkKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
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
}
