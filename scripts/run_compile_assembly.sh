#!/bin/bash
set -ev
bundle exec java mvn clean compile assembly:single
