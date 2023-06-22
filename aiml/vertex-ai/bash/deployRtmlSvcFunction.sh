#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${VERTEX_RTML_HOMEDIR}/../../_bash_/utilities.sh"

echo

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: deployRtmlSvcFunction.sh [-h]" 
   echo "                                [-debug]"
   echo "                                -en <endpointName>"
   echo "       -debug : (Optional) Whether to print out extra information in the functions log."
   echo "       -en    : (Required) Vertex AI deployed model endpoint name."
   echo
}

if [[ $# -eq 0 || $# -gt 3 ]]; then
   usage
   errExit 10 "Incorrect input parametere count!"
fi

debugStr="info"
endpointName=""
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h)       usage; exit 0               ;;
      -debug)   debugStr="debug";           ;;
      -en)      endpointName="$2"; shift;   ;;
      *)        errExit 20 "Unknown input parameter passed: $1" ;;
   esac
   shift
done
debugMsg "debugStrebug=${debugStr}"
debugMsg "endpointName=${endpointName}"

if [[ -z "${endpointName}" ]]; then
   usage
   errExit 30 "Missing required input parameter 'endpointName'!"
fi


useDsrsRestApi=$(getPropVal ${VERTEX_RTML_HOMEDIR}/conf/main-cfg.properties "useDsrsService")
debugMsg "useDsrsRestApi=${useDsrsRestApi}"

VERTEX_RTML_HOMEDIR_APP="${VERTEX_RTML_HOMEDIR}/code/rtml-app"
VERTEX_RTML_HOMEDIR_CONF="${VERTEX_RTML_HOMEDIR}/conf"

funcPkgFile="${VERTEX_RTML_HOMEDIR_APP}/func-mlsvc-caller/target/func-mlsvc-caller-1.0.0.jar"
funcCoreName="rtmlsvccaller"
funcFullName="public/default/${funcCoreName}"
funcCfgJsonFile="${VERTEX_RTML_HOMEDIR_CONF}/pulsar-function/${funcCoreName}.json"

if ! [[ -f "${funcPkgFile}" ]]; then
   errExit 40 "Can't find the required function package jar file ('" + funcPkgFile + "')!"
fi

if ! [[ -f "${funcCfgJsonFile}" ]]; then
   errExit 50 "Can't find the required function confiuration json file ('" + funcCfgJsonFile + "')!"
fi

pulsarShellExistence=$(chkSysSvcExistence pulsar-shell)
debugMsg "pulsarShellExistence=${pulsarShellExistence}"
if [[ ${pulsarShellExistence} -eq 0 ]]; then
    errExit 60 "[ERROR] 'puslar-shell' isn't installed on the local machine, which is required to create Pulsar topics and functions!"
fi

if [[ "${useDsrsRestApi}" == "false" ]]; then
    PROJECT_ID=$(gcloud projects list --filter="$(gcloud config get-value project)" --format="value(PROJECT_NUMBER)")
    LOCATION=$(gcloud config get-value compute/region)
    ENDPOINT_ID=$(gcloud ai endpoints list --region="${LOCATION}" 2>/dev/null | grep ${endpointName} | awk '{print $1}')
    ACCESS_TOKEN=$(gcloud auth application-default print-access-token)

    debugMsg "PROJECT_ID=${PROJECT_ID}"
    debugMsg "LOCATION=${LOCATION}"
    debugMsg "ENDPOINT_ID=${ENDPOINT_ID}"

    if [[ -z "${PROJECT_ID}" || -z "${LOCATION}" ||
          -z "${ENDPOINT_ID}" || -z "${ACCESS_TOKEN}" ]]; then
        errExit 70 "Can't get the current GCP project ID, location, endpoint ID, or the access token!"
    fi
fi

getLocalIp() {
    local LOCAL_IP=""

    if [[ $(uname) == "Darwin" ]]; then
        LOCAL_IP=$(ipconfig getifaddr en0)
    # Check if the operating system is Linux
    elif [[ $(uname) == "Linux" ]]; then
        LOCAL_IP=$(ip addr show | grep -w inet | grep -v 127.0.0.1 | awk '{print $2}' | awk -F '/' '{print $1}')
    else
        echo "Unsupported operating system."
    fi

    echo "${LOCAL_IP}"
}

## for DSRS REST API
if [[ "${useDsrsRestApi}" == "true" ]]; then
    LOCAL_IP=$(getLocalIp)
    REST_ENDPOINT="http://${LOCAL_IP}:8090/avg"
## for 'Fuel Efficiency' ML model REST API
else
    REST_ENDPOINT="https://${LOCATION}-aiplatform.googleapis.com/v1"
fi
debugMsg "REST_ENDPOINT=${REST_ENDPOINT}"

pulsar-shell -e "admin functions create \
  --name rtmlsvccaller \
  --auto-ack true \
  --tenant public \
  --namespace default \
  --jar ${funcPkgFile} \
  --classname com.example.rtmldemo.FuelEfficiencyMlPredSvcFunc \
  --inputs public/default/input \
  --output public/default/result \
  --user-config '{ \"logLevel\": \"${debugStr}\", \"useDsrsRestApi\": \"${useDsrsRestApi}\", \"restEndpoint\": \"${REST_ENDPOINT}\", \"projectId\": \"${PROJECT_ID}\", \"location\": \"${LOCATION}\", \"endpointId\": \"${ENDPOINT_ID}\", \"accessToken\": \"${ACCESS_TOKEN}\"  }'
" --fail-on-error

#   --user-config '{ \"logLevel\": \"debug\", \"useDsrsRestApi\": \"true\", \"restEndpoint\": \"http://192.168.86.77:8090/avg\" }'


###
# NOTE: somehow the following curl command doesn't work
#       it gets stuck after the "100 Continue" stage (as below)
# ......
# > Expect: 100-continue
# > 
# * Done waiting for 100-continue
#   0 1051k    0     0    0     0      0      0 --:--:--  0:00:01 --:--:--     0} [65536 bytes data]
# * We are completely uploaded and fine
# 100 1051k    0     0  100 1051k      0  51095  0:00:21  0:00:21 --:--:--     0^C
# --------------------------------------------------------------------
# curlCmd="curl -sS -k -X POST
#     --write-out '%{http_code}'
#     --output /tmp/curlCmdOutput.txt
#     --url 'http://localhost:8080/admin/v3/functions/${funcFullName}'"

# curlCmd="${curlCmd}
#     --form 'data=@${funcPkgFile};type=application/octet-stream'
#     --form 'functionConfig=@${funcCfgJsonFile};type=application/json'"

# # stderr is needed here because stdout is capatured as the function output
# debugMsg "curlCmd=${curlCmd}"
# httpResponseCode=$(eval ${curlCmd})
# debugMsg "httpResponseCode=${httpResponseCode}"
