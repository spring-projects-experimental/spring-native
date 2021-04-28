#!/usr/bin/env bash

RC=0

docker-compose up -d integration
${PWD%/*samples/*}/scripts/compileWithMaven.sh && ${PWD%/*samples/*}/scripts/test.sh || RC=$?
docker-compose down

exit $RC
