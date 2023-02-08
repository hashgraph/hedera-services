package com.hedera.node.app.config.validation;

import static com.hedera.node.app.spi.config.PropertyNames.SCHEDULING_WHITE_LIST;

import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;

public class SchedulingWhitelistConstraint implements
    ConfigPropertyConstraint<HederaFunctionality> {

  public final static String PROPERTY_NAME = SCHEDULING_WHITE_LIST;

  public final static Class<HederaFunctionality> TYPE = HederaFunctionality.class;


  @Override
  public ConfigViolation check(final PropertyMetadata<HederaFunctionality> propertyMetadata) {
    //TODO: Base API can not handle Constraint for collections?
    if (!MiscUtils.QUERY_FUNCTIONS.contains(propertyMetadata.getValue())) {
      return DefaultConfigViolation.of(propertyMetadata, "Value not in whitelist");
    }
    return null;
  }
}
