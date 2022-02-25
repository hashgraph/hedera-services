#! /bin/sh
cd ..
./mvnw clean install -pl hapi-fees,hapi-utils -am -DskipTests
cd test-clients
../mvnw clean test -Dtest=E2EPackageRunner -Dpackages=$1 -DDEFAULT_PORT=50213