# Sonar quickstart

Here's a short guide for running SonarQube,  
which is a static analysis tool that can be used to analyze the codebase for bugs, code smells, and security vulnerabilities.

Let's start by running sonar manually using official Docker image, and then we'll automate this action for IntelliJ IDEA.

Make sure you have Docker installed and running on your machine.

## Running SonarQube with Docker

This is the easiest way to run SonarQube locally, and it's also flexible enough to apply it to any project submodule.

1. At first, we should start SonarQube server:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube
# It may take a few minutes, check the logs with:
docker logs -f sonarqube
```

2. Prepare a project in SonarQube:

```bash
# Change the default password (to unlock admin account in UI)
PASSWORD=admin1234 && \
curl -v -u admin:admin 'http://localhost:9000/api/users/change_password' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-raw "login=admin&password=$PASSWORD&previousPassword=admin" && \
echo "Credentials updated. Login: admin, password: $PASSWORD"
# ^ if you gen an error here, try running the command again after a few seconds, SonarQube might not be ready yet
  
# Create project
NAME=hedera-services && \
curl -X POST -u admin:$PASSWORD "http://localhost:9000/api/projects/create?name=$NAME&project=$NAME" && \
echo "Project created: $NAME"


# Generate token
TOKEN=$(curl -X POST -u admin:$PASSWORD "http://localhost:9000/api/user_tokens/generate?name=$NAME" | jq -r '.token') && \
echo "Token generated: $TOKEN"
```

3. Then, we can run SonarScanner to analyze our codebase:  

```bash
# Specify the module path you're interested in scanning, for example:
SCAN_MODULE_PATH="hedera-node/cli-clients"
# Run the scanner
echo "Running SonarScanner for projectKey=$NAME and modulePath=$SCAN_MODULE_PATH, token=$TOKEN" && \
docker run --rm \
    -e SONAR_HOST_URL="http://host.docker.internal:9000" \
    -e SONAR_SCANNER_OPTS="-Dsonar.projectKey=${NAME} -Dsonar.token=${TOKEN} -Dsonar.java.binaries=build/classes/java/main" \
    -v "$(pwd)/$SCAN_MODULE_PATH:/usr/src" \
    sonarsource/sonar-scanner-cli
```

Check the results in SonarQube UI: http://localhost:9000/dashboard?id=hedera-services

To shut down and remove SonarQube server run:

```bash
docker rm -f sonarqube
```

## Running SonarQube automatically in IntelliJ IDEA

1. Create run_sonar.sh script in the root of the project:

```bash
#!/bin/bash
SCAN_MODULE_PATH=$1 && \
SONAR_NAME=$2 && \
SONAR_LOGIN="admin" && \
SONAR_PASSWORD="admin1234" && \
docker run --rm \
    -e SONAR_HOST_URL="http://host.docker.internal:9000" \
    -e SONAR_SCANNER_OPTS="-Dsonar.projectKey=${SONAR_NAME} -Dsonar.login=${SONAR_LOGIN} -Dsonar.password=${SONAR_PASSWORD} -Dsonar.java.binaries=build/classes/java/main" \
    -v "$(pwd)/$SCAN_MODULE_PATH:/usr/src" \
    sonarsource/sonar-scanner-cli
```
2. Open Run\Debug Configuration and add a new Shell Script configuration with command:  
`./run_sonar.sh` and options `hedera-node/cli-clients hedera-services`  
Make sure variables are set correctly for your running SonarQube instance.

3. Add a Gradle task you want to run before SonarQube analysis, you should specify the module and build task, for example:
   <img width="713" alt="image" src="https://github.com/hashgraph/hedera-services/assets/22843881/d79d7e2a-9d10-4017-a130-24c19ca9c08f">

Result:
<img width="881" alt="image" src="https://github.com/hashgraph/hedera-services/assets/22843881/5443e993-e056-4509-9a2d-1c8d2ee17b6e">

Now, running the configuration will build the project and start SonarQube right after that.



