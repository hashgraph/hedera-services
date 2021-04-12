# Services configuration

The child folders in this directory hold configuration
for each named environment, appropriate to use **with 
the upcoming release of Services**. Each configuration 
file which can appear is given a small sub-section below. 

All deployment paths are given relative to the top-level 
 directory containing the deployed JAR (in DevOps-managed 
environments, this is _/opt/hgcapp/services-hedera/HapiApp2.0_.) 
If no deployment path is given, a file belongs in the top-level 
directory itself.

## bootstrap.properties
* Deployed as _data/config/bootstrap.properties_
* Normally used with networks **not** starting from a saved state
* Contains any overrides to be used at network startup
  - For example, more permissive bootstrap throttles

## application.properties
* Deployed as _data/config/application.properties_
* Genesis contents of file `0.0.121` 
  - :information_desk_person: &nbsp; _Only_ relevant for networks **not** starting from a saved state 

## optional-upgrade/application.properties
* _Only_ relevant for networks starting from a saved state
* If present, may be used as input to a yahcli `sysfiles upload properties` post-upgrade at the discretion of DevOps

## upgrade/application.properties
* _Only_ relevant for networks that start from a saved state
* If present, should be used as input to a yahcli `sysfiles upload properties` post-upgrade at the request of Product

## api-permission.properties
* Deployed as _data/config/api-permission.properties_
* Genesis contents of file `0.0.122` 
  - :information_desk_person: &nbsp; _Only_ relevant for networks **not** starting from a saved state 

## optional-upgrade/api-permission.properties
* _Only_ relevant for networks starting from a saved state
* If present, may be used as input to a yahcli `sysfiles upload permissions` post-upgrade at the discretion of DevOps

## upgrade/api-permission.properties
* _Only_ relevant for networks that start from a saved state
* If present, should be used as input to a yahcli `sysfiles upload permissions` post-upgrade at the request of Product

## node.properties
* Deployed as _data/config/node.properties_
* Overrides of node-specific configuration
  - For example, more permissive Netty HTTP/2 settings

## upgrade/throttles.json
* _Only_ relevant for networks that start from a saved state
* If present, should be used as input to a yahcli `sysfiles upload throttles` post-upgrade at the request of Product

## javaOptions
* Script to start `HGCApp` with all needed options

## log4j2.xml
* Services logging configuration

## settings.txt
* Platform configuration
* Performance-sensitive settings include:
  - `throttleTransactionQueueSize`
  - `throttle7extra`
  - `cpuVerifierThreadRatio`
  - `cpuDigestThreadRatio`
  - `maxOutgoingSyncs`
