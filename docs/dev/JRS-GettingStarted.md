# Java Regression Suite(JRS) Developer Testing
# **Table of Contents**

- [Description](#toc-description)
- [Overview](#overview)
    - [File Types](#file-types)
    - [Naming Conventions](#naming-conventions)
    - [Instructions for kicking off Regression](#instructions)
- [Current Nightly Regression](#nightly-regression)
    - [Tests in nightly](#test-description)
- [GCP SetUp](#gcp_setup)

<a name="toc-description"></a>

# **Description**
This document describes the steps needed to run a JRS test using the infrastructure in 
swirlds-platform-regression. It also has the set up steps for GCP.

<a name="overview"></a>

# **Overview**
The Java Regression Suite (JRS) runs on a remote machine. It relies on two types of json files, i.e., Regression configuration json and experiment configuration json described below.

##### **File Types**
1. Regression configuration JSON : It usually starts with `GCP`. Based on the credentials after [GCP setup](#gcp_setup), it holds information of 
     - `cloud` cloud configuration that includes the private keys, regions in which nodes need to be instantiated etc.,
     - `experiments` experiments to run
     - `slack` slack details needed to post the results
     - `result` results folder in the local machine to download the logs after test  
     -  `db` postgres information 
     - other minor details
2. Experiment configuration JSON : It holds specific information on the set up of the experiment and validations to be done to tell if it passed.  This majorly includes 
   - `duration` duration of the test 
   - `settings` platform settings 
   - `runConfig` types of the run configuration based on the test type 
   - `hederaServicesConfig`  configuration for the clients when running services tests
   - `app` application jar to be run (HederaNode.jar for services)
   - `startSavedState` saved state location, if any saved state needs to be loaded at the beginning of test
   - `validatorConfigs` to be run at end of the experiment to validate if it passed 

In order to avoid duplication of data, both JSONs support inheritance from `parentList`. Settings, configurations, validators etc., can be defined in a parent JSON and can be reused as defined in the example below.

```
"parentList": [
    "configs/services/default/standard-services-settings.json"
	"configs/services/default/standard-services-configs.json",
	"configs/services/default/standard-services-validators.json"
  ],
```

**NOTE:** For any developer to run tests, a personal Regression JSON should be created with their personal cloud configuration details.
It should be created under `swirlds-platform/regression/configs/services/suites/personal/GCP-Personal-XXX.json` (XXX is name of the developer)

<a name="naming-conventions"></a>
##### **Naming Conventions for the JSONs** 
Naming conventions described in [file](https://github.com/swirlds/swirlds-platform-regression/blob/develop/docs/regression-test-naming-standards.md) are required to be followed for Regression configuration JSON and Experiment configuration JSON. 
Any new naming conventions need to be added to the file if required.


<a name="instructions"></a>
##### **Instructions for kicking off Regression**
- Open terminal
- Clone `hedera-services` and `swirlds-platform` repositories
- `cd ~/swirlds-platform/regression; 
  ./regression_services.sh configs/services/suites/personal/GCP-Personal-XXX.json path_to_hedera-services_repository > cron-personal-test.err 2>&1`
    
- Add `& disown -h` at the nd of the above command if it needs to run in background . If not use screen.


<a name="nightly-regression"></a>

# **Services Nightly Regression**

Current Services nightly regression runs the following tests based on the cron timings defined in [config.yml](https://github.com/hashgraph/hedera-services/blob/master/.circleci/config.yml).
- Performance
- Restart
- State Recovery
- Reconnect
- Migration
- Network Error
- Network Delay
- Basic
- AccountBalances Validate
- Update


To validate the regression results follow steps defined in [regression-validation-checklist.md](https://github.com/swirlds/swirlds-platform-regression/blob/develop/docs/regression-validation-checklist.md)



<a name="gcp_setup"></a>

# **GCP setup to run tests**

Steps to set up GCP are listed in this [document](https://hederatest.sharepoint.com/:w:/r/sites/Engineering/_layouts/15/doc2.aspx?sourcedoc=%7BC5828078-0D3F-46CA-BD31-8D883C718E4A%7D&file=GCPUSERSETUP.docx&action=default&mobileredirect=true&cid=f367aab3-8373-4728-a4c4-a07a36f6234e) 