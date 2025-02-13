// SPDX-License-Identifier: Apache-2.0
/**
 * The package provides an api to validate configuration properties at creation time of a configuration. The interface
 * {@link com.swirlds.config.api.validation.ConfigValidator} is the most basic way to add a validation (see
 * {@link
 * com.swirlds.config.api.ConfigurationBuilder#withValidator(com.swirlds.config.api.validation.ConfigValidator)}). When
 * working with config data object (see {@link com.swirlds.config.api.ConfigData}) the
 * {@link com.swirlds.config.api.validation.annotation} package provides annotations to add validation to the config
 * data objects.
 *
 * @see com.swirlds.config.api.validation.ConfigValidator
 * @see com.swirlds.config.api.ConfigurationBuilder#withValidator(com.swirlds.config.api.validation.ConfigValidator)
 */
package com.swirlds.config.api.validation;
