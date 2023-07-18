#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCENARIO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../../.." &> /dev/null && pwd )

source "${SCENARIO_HOMEDIR}/_bash/utilities.sh"
# DEBUG=true
echo

##
# Show usage info
#
usage() {
    echo
    echo "Usage: deploy_spark_k8s.sh [-h]"
    echo "                           [-portForwardOnly]"
    echo "                           [-sparkDeployDir <spark_deployment_directory>]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo "       -sparkDeployDir  : (Optional) The directory that has the Spark deployment yaml files."
    echo
}

if [[ $# -gt 4 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
sparkDeployDir="${SCENARIO_HOMEDIR}/_deployment/spark-k8s"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        -sparkDeployDir) sparkDeployDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "sparkDeployDir=${sparkDeployDir}"

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

k8sNamespace=spark

if [[ ${portForwardOnly} -eq 0 ]]; then
    echo ">> Create the K8s namespace ${k8sNamespace} if doesn't exist ..."
    existingSparkNs=$(kubectl get namespace | grep ${k8sNamespace})
    if [[ -z "${existingSparkNs}" ]]; then
      kubectl create namespace ${k8sNamespace}
    fi
    echo

    echo ">> Install the Spark master ..."
    echo "   - deploy \"spark-master\" deployment"
    kubectl -n ${k8sNamespace} create -f ${sparkDeployDir}/spark-master-deployment.yaml
    echo "   - wait for \"spark-master\" deployment to be ready"
    kubectl wait -n ${k8sNamespace} deployment spark-master --timeout=600s --for condition=Available=True
    echo "   - deploy the \"spark-master\" service"
    kubectl -n ${k8sNamespace} create -f ${sparkDeployDir}/spark-master-service.yaml


    echo ">> Install the Spark worker(s) ..."
    echo "   - deploy \"spark-worker\" deployment"
    kubectl -n ${k8sNamespace} create -f ${sparkDeployDir}/spark-worker-deployment.yaml
    echo "   - Wait for \"spark-worker\" deployment to be ready"
    kubectl wait -n ${k8sNamespace} deployment spark-worker --timeout=600s --for condition=Available=True
    echo "   - deploy \"spark-worker\" service"
    kubectl -n ${k8sNamespace} create -f ${sparkDeployDir}/spark-worker-service.yaml
fi

echo ">> Forward the following Spark ports to localhost: ${SPARK_PORTS[@]} ..."
sparkMasterSvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="spark-master" -o name)
if [[ -n "${sparkMasterPodName// }" ]]; then
    startK8sPortForward \
        ${k8sNamespace} \
        ${sparkMasterSvcName} \
        "spark_master_port_forward.nohup" \
        "${SPARK_MASTER_PORTS[@]}"

    sparkWorkerSvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="spark-worker" -o name)
    if [[ -n "${sparkWorkerSvcName// }" ]]; then
       startK8sPortForward \
          ${k8sNamespace} \
          ${sparkWorkerSvcName} \
          "spark_worker_port_forward.nohup" \
          "${SPARK_WORKER_PORTS[@]}"
    else
        echo "   [WARN] Doesn't detect the corresponding Spark worker service, skip worker port forwarding!"
    fi
    echo
else
   echo "   [WARN] Doesn't detect the corresponding Spark master service, skip Spark port forwarding!"
fi
echo
