#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

echo

dockerExistence=$(chkSysSvcExistence docker)
debugMsg "dockerExistence=${dockerExistence}"
if [[ ${dockerExistence} -eq 0 ]]; then
    errExit 30 "[ERROR] 'docker' isn't installed on the local machine, which is required to build the model training docker image!"
fi

gcloudExistence=$(chkSysSvcExistence gcloud)
debugMsg "gcloudExistence=${gcloudExistence}"
if [[ ${gcloudExistence} -eq 0 ]]; then
    errExit 30 "[ERROR] 'gcloud' isn't installed on the local machine, which is required to publish the model training docker image!"
fi

PROJECT_ID=$(gcloud config list --format 'value(core.project)')
IMAGE_URI="gcr.io/${PROJECT_ID}/fueleffpred:latest"
docker build ./ -t ${IMAGE_URI}
docker push ${IMAGE_URI}