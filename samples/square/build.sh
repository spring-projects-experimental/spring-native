#!/usr/bin/env bash
# export DEBUG=true
# shellcheck disable=SC2046
START=$( cd $(dirname $0) && pwd )
echo "starting in $START"
rm -rf $START/target || echo "could not delete the target directory (it might not exist)"
mkdir -p ${START}/target/classes/META-INF/native-image
mvn -DskipTests=true -Pnative -f $START/pom.xml clean  package &&  $START/target/square
