# GCP SetUp for JRS Testing
# **Table of Contents**

- [Pre-setup](#pre-setup)
- [Initial set up](#initial-setup)
- [gcloud setup](#gcloud-setup)
- [Set up personal JSON](#json-setup)
- [Creating instance manually](#manual-creation)
- [Delete instance manually](#delete-manually)

<a name="pre-setup"></a>

# **Pre SetUp**
1. Follow directions in e-mail `Welcome to your new Google Account for Hashgraph` from `google workspace team`. You will need either a physical security or google to use your cell phone for 2FA
2. Send you public key you are planning to use to GCP admin in order to make sure you can access all machines

<a name="initial-setup"></a>

# **Initial SetUp**
1. Go to `console.cloud.google.com`
2. Select your `hedera.com` email address to use
3. Agree to the ToS
4. Click on `Select a Project` on the top most menu
5. Select `Swirlds.com -> Hedera Regression`. If you do not see `Swirlds Regression` click on the “ALL” tab and follow the Swirlds folders down till you find `Swirlds Regression`

<a name="gcloud-setup"></a>

# **Gcloud SetUp**
To run regression from your computer you will need to install gcloud on your computer

1. Follow the instructions to install the sdk for your OS at [LINK](https://cloud.google.com/sdk/docs/install) 
2. After successful installation, open terminal and  run `gcloud init`
3. When asked to login agree: `Y` 
4. It provides a link to login. Follow the link in browser (make sure you are logged into browser with your hedera.com gmail account).
    - Click Allow, if `Google Cloud SDK wants to access the Google Account` 
    - Copy Code you are given
    - Paste into the waiting field on command prompt
    
5. Pick the cloud project to use as `hedera-regression`
6. Configure default compute region, by selecting `Y` and pick `us-east1-c`


<a name="json-setup"></a>

# **Personal JSON SetUp for Regression**
As defined in the example below, set up your personal JSON with the following details
1. `name` should be GCP-Personal-<USER_NAME>
2. `keyLocation` provide the location of `pem` file. Please ensure the `.pem` extension is added to your private key.
3. `login` should match your hedera email address prefix

![cloud-config](/Users/neeharikasompalli/Documents/cloud-config.png)


<a name="manual-creation"></a>

# **Creating instances manually**

JRS provisions instances for the regression tests. If in any case, we need to create instances manually follow the steps below
1. From the main menu `Google Cloud Platform` go to `Compute Engine -> Instance Groups`
2. Click `Create instance Group` at the top
3. Select the following options:
    - Name: <username>-<branchname>-<year><month><day><militarytime>
    - Select Single Zone   
    - Region: us-east1
    - Instance template:  select the highest numbered `atf-node-instance-template`. 
        - Currently `atf-node-instance-template-9-256hd` for servers
        - Currently `atf-node-instance-template-9-10hd-4cpu-8mem` for clients
    - Autoscaling mode: select `Don’t autoscale`
    - Number of Instances: Number of server nodes + zero stake nodes or number of clients. This option can’t be set until autoscale is turned off
    
4. Click on Create
5. Click on `VM Instances` on left-hand menu to see individual instances and get IP addresses.
   
**NOTE:** Two separate instance groups should be created for servers and clients for services regression.

**NOTE:** Individual instances inside an instance group can't be deleted. You must delete the group from `Instance Groups` page
<a name="delete-manually"></a>

# **Deleting instances manually**

JRS shuts down the instances provisioned at the end of test. Sometimes if the user kills the test run in the middle, and google cloud instances are not shut down, delete the instances manually by following the steps below.

1. From the main menu in `Google Cloud Platform` go to ` Compute Engine -> Instance Groups` 
2. Select the groups with your name that should not be running
3. Click `Delete` on the top menu