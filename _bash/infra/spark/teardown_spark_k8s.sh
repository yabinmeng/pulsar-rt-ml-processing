#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCENARIO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${SCENARIO_HOMEDIR}/_bash/utilities.sh"
# DEBUG=true
echo

##
# Show usage info
#
usage() {
    echo
    echo "Usage: deploy_Flink_k8s.sh [-h]"
    echo "                           [-portForwardOnly]"
    echo "                           [-FlinkDeployDir <Flink_deployment_directory>]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo "       -FlinkDeployDir  : (Optional) The directory that includes Flink deployment yaml files."
    echo
}

if [[ $# -gt 4 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
FlinkDeployDir="${SCENARIO_HOMEDIR}/_deployment/Flink-k8s"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        -FlinkDeployDir) FlinkDeployDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "FlinkDeployDir=${FlinkDeployDir}"

kubeCtlExistence=$(chkSysSvcExistence kubectl)
debugMsg "kubeCtlExistence=${kubeCtlExistence}"
if [[ ${kubeCtlExistence} -eq 0 ]]; then
    errExit 30 "'kubectl' isn't installed on the local machine, which is required to build scenario programs!"
fi

if ! [[ -d "${sparkDeployDir}" &&
        -f "${sparkDeployDir}/spark-master-deployment.yaml" &&
        -f "${sparkDeployDir}/spark-master-service" &&
        -f "${sparkDeployDir}/spark-worker-deployment.yaml" &&
        -f "${sparkDeployDir}/spark-worker-service" ]]; then
    errExit 40 "Can't find the required spark deployment yaml files in the specified directory: \"${sparkDeployDir}\"."
fi

k8sNamespace=Spark

echo ">> Tear down Spark worker service and deployment ..."
sparkWorkerSvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="spark-worker" -o name)
debugMsg "sparkWorkerSvcName=${sparkWorkerSvcName}"
if [[ -n "${sparkWorkerSvcName// }" ]]; then
    echo
    echo "   - Terminate the forwarded Spark work port: ${SPARK_PORTS[@]} ... "
    stopK8sPortForward "${SPARK_PORTS[@]}"
    echo

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo "   - Uninstall the Spark cluster from the K8s namespace \"${k8sNamespace}\" ... "
        echo "     * tear down Spark worker(s) ..."
        kubectl delete -f ${sparkDeployDir}/Spark-worker-service.yaml
        kubectl delete -f ${sparkDeployDir}/Spark-worker-deployment.yaml
        echo "     * tear down Spark master ..."
        kubectl delete -f ${sparkDeployDir}/Spark-master-service.yaml
        kubectl delete -f ${sparkDeployDir}/Spark-master-deployment.yaml
        echo
    fi
fi
echo
