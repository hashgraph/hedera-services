package com.hedera.node.app.config.validation;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import java.util.stream.Stream;

public class ParseableValueValidator implements ConfigValidator {

  @Override
  public Stream<ConfigViolation> validate(final Configuration configuration) {
    //TODO:
    //ScreenedSysFileProps#hasParseableValue
    return Stream.empty();
  }
}
