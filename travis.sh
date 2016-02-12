#!/bin/bash

set -euo pipefail

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

cat ~/.m2/settings.xml

if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  strongEcho 'Build and analyze commit in master'
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent verify sonar:sonar \
    -Pcoverage-per-test -Dmaven.test.redirectTestOutputToFile=false -B -e -V \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN


elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]; then
  strongEcho 'Build and analyze pull request'
  
  # integration of jacoco report is quite memory-consuming
  export MAVEN_OPTS="-Xmx1G -Xms128m"
  
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent verify sonar:sonar \
    -Pcoverage-per-test -Dmaven.test.redirectTestOutputToFile=false -B -e -V \
    -Dsonar.analysis.mode=issues \
    -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
    -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
    -Dsonar.github.oauth=$SONAR_GITHUB_OAUTH \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN

else
  strongEcho 'Build, no analysis'
  # Build branch, without any analysis

  # No need for Maven goal "install" as the generated JAR file does not need to be installed
  # in Maven local repository
  mvn verify -Dmaven.test.redirectTestOutputToFile=false -B -e -V
fi
