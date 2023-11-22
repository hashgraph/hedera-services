package com.hedera.node.config.converter;


import com.hedera.node.config.types.Profile;
import com.swirlds.config.api.converter.ConfigConverter;

public class ProfileConverter extends AbstractEnumConfigConverter<Profile> implements ConfigConverter<Profile> {
    @Override
    protected Class<Profile> getEnumType() {
        return Profile.class;
    }
}
