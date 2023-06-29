#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
KASKADA_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${KASKADA_RTML_HOMEDIR}/../_bash_/utilities.sh"

echo

usage() {
   echo
   echo "Usage: forward_kaskada_port.sh [-h]"
   echo "                               -namespace <namespace>"
   echo "                               -svcName <k8s_servie_name>"
   echo "                               -act <start|stop>"
   echo "       -h : Show usage info"
   echo "       -namespace : The K8s namespace in which the Kaskada cluster is deployed."
   echo "       -svcName: The K8s Kaskada servcie name"
   echo "       -act: Start or stop port forwarding"
   echo
}

if [[ $# -eq 0 || $# -gt 7 ]]; then
   usage
   errExit 20
fi

while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -namespace) k8sNamespace=$2; shift ;;
      -svcName) svcNameName=$2; shift ;;
      -act) actTerm=$2; shift ;;
      *) echo "[ERROR] Unknown parameter passed: $1"; exit 30 ;;
   esac
   shift
done
debugMsg "k8sNamespace=${k8sNamespace}"
debugMsg "svcNameName=${svcNameName}"
debugMsg "actTerm=${actTerm}"

if [[ -z "${k8sNamespace// }" ]]; then
    echo "[ERROR] The K8s namespace name must be provided!"
    errExit 40;
fi

if [[ -z "${svcNameName// }" ]]; then
    echo "[ERROR] The K8s service name must be provided!"
    errExit 50;
fi

if ! [[ "${actTerm}" == "start" || "${actTerm}" == "stop" ]]; then
    echo "[ERROR] Invalid value for '-act' option; must be either 'start' or 'stop'!"
    errExit 60;
fi

if [[ "${actTerm}" == "start" ]]; then
    startK8sPortForward \
        ${k8sNamespace} \
        ${svcNameName} \
        "kaskada_port_forward.nohup" \
        "${KASKADA_PORTS[@]}"
else
    stopK8sPortForward "${KASKADA_PORTS[@]}"
fi