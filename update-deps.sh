#!/bin/bash

./mvnw versions:use-latest-releases \
  -DallowSnapshots=false \
  -DallowMajorUpdates=false \
  -DrulesUri=file:./versions-rules.xml \
  -Dexcludes='org.springframework.boot:*,org.springframework:*,org.springframework.security:*,io.micrometer:*,org.springframework.data:*' \
  -DgenerateBackupPoms=false
