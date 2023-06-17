#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

usage() {
   echo
   echo "Usage: forward_proxy_port.sh [-h]"
   echo "                             -namespace <namespace>"
   echo "                             -act <start|stop>"
   echo "                             -proxySvc <proxy_servie_name>"
   echo "                             -tlsEnabled <true|false>"
   echo "       -h : Show usage info"
   echo "       -namespace : The K8s namespace in which the Pulsar cluster is deployed."
   echo "       -act: start or stop port forwarding"
   echo "       -proxySvc: Pulsar proxy servcie name"
   echo "       -tlsEnabled: Whether TLS port needs to be forwarded"
   echo
}

if [[ $# -eq 0 || $# -gt 8 ]]; then
   usage
   errExit 20
fi

while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -namespace) k8sNamespace=$2; shift ;;
      -act) actTerm=$2; shift ;;
      -proxySvc) proxySvcName=$2; shift ;;
      -tlsEnabled) tlsEnabled=$2; shift ;;
      *) echo "[ERROR] Unknown parameter passed: $1"; exit 30 ;;
   esac
   shift
done
debugMsg "k8sNamespace=${k8sNamespace}"
debugMsg "actTerm=${actTerm}"
debugMsg "proxySvcName=${proxySvcName}"
debugMsg "tlsEnabled=${tlsEnabled}"

if [[ -z "${k8sNamespace// }" ]]; then
    echo "[ERROR] K8s namespace name must be provided!"
    errExit 40;
fi

if ! [[ "${actTerm}" == "start" || "${actTerm}" == "stop" ]]; then
    echo "[ERROR] Invalid value for '-act' option; must be either 'start' or 'stop'!"
    errExit 50;
fi

if [[ -z "${proxySvcName// }" ]]; then
    echo "[ERROR] Pulsar proxy service name must be provided!"
    errExit 60;
fi

if [[ -n "${tlsEnabled// }" && "${tlsEnabled}" != "true" && "${tlsEnabled}" != "false" ]]; then
    echo "[ERROR] Invalid value for '-tlsEnabled' option; must be either 'true' or 'false'!"
    errExit 70;
fi

if [[ "${actTerm}" == "start" ]]; then
    startProxyPortForward \
        ${k8sNamespace} \
        ${proxySvcName} \
        ${tlsEnabled} \
        "pulsar_proxy_port_forward.nohup"
else
    stopProxyPortForward ${proxySvcName}
fi