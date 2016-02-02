#!/bin/bash

set -euo pipefail

# temporary use repox.sonarsource.com/sonarsource as Maven repository. It contains
# the SonarQube 5.3 artifacts which are deployed in central repository.

function configureTravis {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v23 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

mvn verify -B -e -V
