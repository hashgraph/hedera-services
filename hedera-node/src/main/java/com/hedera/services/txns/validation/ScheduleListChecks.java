package com.hedera.services.txns.validation;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ScheduleListChecks {
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

    public static ResponseCodeEnum checkKey(Key key, ResponseCodeEnum failure) {
        try {
            var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return failure;
            }
            return OK;
        } catch (Exception ignore) {
            return failure;
        }
    }
}
