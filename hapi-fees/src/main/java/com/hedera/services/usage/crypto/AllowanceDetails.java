package com.hedera.services.usage.crypto;

import java.util.List;

public record AllowanceDetails(Boolean approvedForAll, List<Long> serialNums) {
}
