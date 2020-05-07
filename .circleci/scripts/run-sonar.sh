#!/usr/bin/env bash
cp ${REPO}/.circleci/sonar-project.properties /sonar-scanner/conf/sonar-scanner.properties
/sonar-scanner/bin/sonar-scanner -Dsonar.login="${sonartoken}"
