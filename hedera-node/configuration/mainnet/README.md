# Settings for Next Release
A place holder for all settings to be used in next release
Should reflect values necessary for production 
Enginerring  is responsible to maintain settings in this folder
All necessary changes should be made in this folder prior to production release 
Terms:
* topLevelFolder: /opt/hgcapp/services-hedera/HapiApp2.0, where HGCApp is deployed

## settings.txt
* Settings for platform
* To be deployed in topLevelFolder
* **Some settings to be determined by performance test:**
    * throttleTransactionQueueSize
    * throttle7extra
    * cpuVerifierThreadRatio
    * cpuDigestThreadRatio
    * maxOutgoingSyncs

## application.properties
* Settings for HGCApp
* To be deployed in topLevelFolder/data/config
* **Throttle settings to be determined by performance test:**
    * throttlingTps
    * simpletransferTps
    * getReceiptTps
    * queriesTps

## api-permission.properties
* Settings for API's that HGCApp supports
* To be deployed in topLevelFolder/data/config

## log4j2.xml
* Settings for all logging output
* To be deployed in topLevelFolder

## javaOptions
* Script to start HGCApp with all needed options
