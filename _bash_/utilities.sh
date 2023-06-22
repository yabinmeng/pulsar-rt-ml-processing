#! /usr/local/bin/bash

DEBUG=true

# 6650: Pulsar native protocol port
# 8080: Pulsar web admin port
# 9092: Kafka client listenting port
# 8081: Kafka schema registry port (this is also TLS port)
PULSAR_PROXY_PORTS=(6650 8080 9092 8081)
# 6651: Pulsar native protocol TLS port
# 8443: Pulsar web admin TLS port
# 9093: Kafka client listenting TLS port
# 8081: Kafka schema registry TLS port
PULSAR_PROXY_PORTS_TLS=(6651 8443 9093)

# 3365 - REST API port
# 50051 - gRPC API port
KASKADA_PORTS=(3365 50051)

##
# Show debug message
# - $1 : the message to show
debugMsg() {
    if [[ "${DEBUG}" == "true" ]]; then
        if [[ $# -eq 0 ]]; then
            echo
        else
            echo "[Debug] $1"
        fi
    fi
}

##
# Exit bash execution with the specified return value
#
errExit() {
    echo $2
    exit $1
}

##
# Check if the helm repo has been installed locally
#
chkHelmRepoExistence() {
    local namespace=${1}
    local providedRepoName=${2}

    local localRepoName="$(helm -n ${namespace} list | tail +2 | grep ${providedRepoName} | awk '{print $1}')"
    if [[ -z "${localRepoName// }" ]]; then
        echo 0
    else
        echo 1
    fi
}

##
# Only applies to GitHub repo with releases
# - $1: the URL that points to the latest release
#       e.g. https://github.com/some/repo/releases/latest
chkGitHubLatestRelVer() {
    local verStr=$(curl -sI $1 | awk -F '/' '/^location/ {print  substr($NF, 1, length($NF)-1)}' | sed 's/[^0-9.]*//g' )
    echo "$verStr"
}

##
# Forward the K8s service ports to localhost
# - $1: K8s namespace name
# - $2: K8s service name
# - $3: nohup output file
# - $4: Ports arrary to forward (this MUST be the last argument)
startK8sPortForward() {
    local namespace=${1}
    local svcName=${2}
    local nohupOutFile=${3}
    local ports=()

    i=0
    for arg in "$@"; do
        if [[ $i -ge 3 ]]; then
            ports+=(${arg})
        fi
        i=$((i+1))
    done

    echo > ${nohupOutFile}
    for port in "${ports[@]}"; do
        # echo "   - port ${port}"
        kubectl -n ${namespace} port-forward ${svcName} ${port}:${port} >> ${nohupOutFile} 2>&1 &
    done
}

##
# Terminate the forwarded ports of the Pulsar proxy service
# - $1: Ports arrary that are forwarded (this MUST be the last argument)
stopK8sPortForward() {
    local ports=()
    
    i=0
    for arg in "$@"; do
        if [[ $i -ge 0 ]]; then
            ports+=(${arg})
        fi
        i=$((i+1))
    done
    
    for port in "${ports[@]}"; do
        local pid=$(ps -ef | grep port-forward | grep "${port}" | awk '{print $2}')
        if [[ -n ${pid// } ]]; then
            kill -TERM ${pid}
        fi
    done
}


##
# Check if the required executeable (e.g. docker, kind) has been installed locally
#
chkSysSvcExistence() {
    local whichCmdOutput=$(which ${1})
    if [[ -z "${whichCmdOutput// }" ]]; then
        echo 0
    else
        echo 1
    fi   
}


##
# Read the properties file and returns the value based on the key
# 2 input prarameters:
# - 1st parameter: the property file to scan
# - 2nd parameter: the key to search for
getPropVal() {
    local propFile=$1
    local searchKey=$2
    local value=$(grep "${searchKey}" ${propFile} | grep -Ev "^#|^$" | cut -d'=' -f2)
    echo $value
}