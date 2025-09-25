#!/bin/bash

./mvnw versions:update-parent \
  -DallowMajorUpdates=false \
  -DrulesUri=file:./versions-rules.xml \
  -DgenerateBackupPoms=false
