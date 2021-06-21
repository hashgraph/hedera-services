package com.hedera.services.txns;

import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;

import java.util.List;

public interface CustomFeeSchedules {
	List<CustomFee> lookupScheduleFor(EntityId token);
}
