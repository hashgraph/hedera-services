package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.swirlds.config.api.converter.ConfigConverter;

public class StakeStartupHelperRecomputeTypeConverter implements
    ConfigConverter<StakeStartupHelper.RecomputeType> {

  @Override
  public StakeStartupHelper.RecomputeType convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return StakeStartupHelper.RecomputeType.valueOf(value);
  }
}
