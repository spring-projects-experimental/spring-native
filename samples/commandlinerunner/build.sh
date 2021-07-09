#!/usr/bin/env bash

${PWD%/*samples/*}/scripts/compileWithMaven.sh $* && ${PWD%/*samples/*}/scripts/test.sh
