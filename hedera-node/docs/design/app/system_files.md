# System Files

|        Name         | FileNum *) |           Record            |    Handling Class     |
|---------------------|------------|-----------------------------|-----------------------|
| addressBook         | 101        | `NodeAddressBook`           | N/A                   |
| nodeDetails         | 102        | `NodeAddressBook`           | N/A                   |
| feeSchedules        | 111        | `CurrentAndNextFeeSchedule` | `FeeManager`          |
| exchangeRates       | 112        | `ExchangeRateSet`           | `ExchangeRateManager` |
| networkProperties   | 121        | `ServicesConfigurationList` | `ConfigProviderImpl`  |
| hapiPermissions     | 122        | `ServicesConfigurationList` | `ConfigProviderImpl`  |
| throttleDefinitions | 123        | `ThrottleDefinitions`       | `ThrottleManager`     |
| softwareUpdateZip   | 150        | N/A                         | N/A                   |

*) FileNum is configurable, but we usually use the default values listed here.
