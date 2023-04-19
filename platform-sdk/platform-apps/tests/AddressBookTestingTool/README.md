# AddressBookTestingTool Instructions

This document describes various manual tests that were performed using this testing app. This playbook should be used when designing automated tests using this app.

## Global Modifications and Starting Conditions

Be sure to clean and assemble the whole project if the java files have been modified.

### config.txt
comment out the current app
```
# app,		HashgraphDemo.jar,	   1,0,0,0,0,0,0,0,0,0, all
```
uncomment the AddressBookTestingTool.jar
```
 app,    AddressBookTestingTool.jar,
```

### settings.txt

Add the following lines to the settings.txt file

```
addressBookTestingTool.softwareVersion, 1
addressBookTestingTool.weightingBehavior, 1
```
## Testing Genesis Behavior

### Test Scenario 1: Force Use of Config Address Book on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    0
addressBook.forceUseOfConfigAddressBook,  true
addressBookTestingTool.testScenario,      genesisForceUseOfConfigAddressBookTrue
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```
and
```
AddressBookTestingToolState: Validating test scenario 1.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for files
  * usedAddressBook_v1_<date>.txt
    * matches the addresses in the config.txt, including weight value.
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * the used address book text says `The Configuration Address Book Was Used.`

### Test Scenario 2: Unforced use of Config Address Book on Genesis
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    0
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      genesisForceUseOfConfigAddressBookFalse
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: The loaded signed state is null. The candidateAddressBook is set to genesisSwirldState.updateWeight(configAddressBook, null).
```
and
```
AddressBookTestingToolState: Validating test scenario 2.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for files
  * usedAddressBook_v1_<date>.txt
    * contains the addresses in the config.txt with identical weight
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * the state saved address book was null
    * the used address book text says `The Configuration Address Book Was Used.`

## Testing Non-Genesis Behavior, No Software Upgrade

### Test Scenario 3: No Software Upgrade, Use Saved State Address Book
#### Instructions
1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      skipValidation
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds
4. Stop the app
5. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      noSoftwareUpgradeUseSavedStateAddressBook
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
6. Run the app for 60 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: No Software Upgrade. Continuing with software version 1 and using the loaded signed state's address book and weight values.
```
and
```
AddressBookTestingToolState: Validating test scenario 3.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * contains the addresses in the config.txt, all with weight 10.
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book matches the content of the non-debug .txt file. (differs from previous section)**
    * **the used address book has the text `The State Saved Address Book Was Used.` (differs from previous section)**

### Test Scenario 4: No Software Upgrade, Force Use of Config Address Book
#### Instructions

1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      skipValidation
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds
4. Stop the app
5. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  true
addressBookTestingTool.testScenario,      noSoftwareUpgradeForceUseOfConfigAddressBook
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
6. Run the app for 60 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```
and
```
AddressBookTestingToolState: Validating test scenario 4.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, including weight value. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **the state saved address book contains the addresses in the config.txt, all with weight 10. (differs from previous section)**
    * **the used address book has the text `The Configuration Address Book Was Used.` (differs from previous section)**

### Test Scenario 5: Software Upgrade, Weighting Behavior 2
#### Instructions

1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      skipValidation
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds
4. Stop the app
5. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      softwareUpgradeWeightingBehavior2
addressBookTestingTool.softwareVersion,   2
addressBookTestingTool.weightingBehavior, 2
```
6. Run the app for 60 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Software Upgrade from version 1 to 2. The address book weight will be updated by the saved state's SwirldState.
```
and
```
AddressBookTestingToolState: Validating test scenario 5.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, but the weight values incrementally increase starting from 0. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * The state saved address book is the same as what is in config.txt
    * **the used address book matches the content of the non-debug .txt file. (differs from previous section)**

### Test Scenario 6: Software Upgrade, Force Use Of Config Address Book
#### Instructions

1. Delete `sdk/data/saved` directory if it exists
2. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  false
addressBookTestingTool.testScenario,      skipValidation
addressBookTestingTool.softwareVersion,   1
addressBookTestingTool.weightingBehavior, 1
```
3. Run the app for 60 seconds
4. Stop the app
5. Ensure settings.txt has the following values
```
state.saveStatePeriod,                    10
addressBook.forceUseOfConfigAddressBook,  true
addressBookTestingTool.testScenario,      softwareUpgradeForceUseOfConfigAddressBook
addressBookTestingTool.softwareVersion,   2
addressBookTestingTool.weightingBehavior, 2
```
6. Run the app for 60 seconds.

#### Validation

* check the swirlds.log for the text

```
AddressBookInitializer: Overriding the address book in the state with the address book from config.txt
```
and
```
AddressBookTestingToolState: Validating test scenario 5.
```

* check that there are no errors or exceptions in the swirlds.log

Errors are logged if any of the following conditions are violated.

* check the directory `sdk/data/saved/address_book` for the latest files
  * usedAddressBook_v1_<date>.txt
    * **matches the addresses in the config.txt, including weight value. (differs from previous section)**
  * usedAddressBook_v1_<date>.txt.debug
    * The configuration address book is the same as what is in config.txt
    * **The state saved address book is the config.txt addresses with weight values incrementally increasing starting from 0 (differs from previous section)**
    * **the used address book matches the content of the non-debug .txt file. (differs from previous section)**
