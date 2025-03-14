package org.cloudbus.cloudsim.serverless;

import org.apache.commons.lang3.tuple.Pair;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerHost;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaasFunctionAutoScaler  extends FunctionAutoScaler {

    public MaasFunctionAutoScaler(ServerlessDatacenter dc) {
        super(dc);
    }

    @Override
    public void scaleFunctions(){
        int userId = 0;
        if (Constants.FUNCTION_HORIZONTAL_AUTOSCALING) {
            HashMap<String, Map<String, Integer>> globalFunctionContainerMap  = new HashMap<>();
            HashMap<String, HashMap<String, ArrayList<Integer>>> functionsNormalizedMetrics = new HashMap<>();
            List<? extends ContainerHost> hostList = getServerlessDatacenter().getVmAllocationPolicy().getContainerHostList();
            for (ContainerHost host: hostList) {
                for (ContainerVm machine: host.getVmList()) {
                    userId = machine.getUserId();
                    MaasServerlessInvoker vm = (MaasServerlessInvoker) machine;
                    for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMap().entrySet()) {
                        if (!globalFunctionContainerMap.containsKey(entry.getKey())) {
                            if (!entry.getValue().isEmpty()) {
                                HashMap<String, Integer> fnMap = new HashMap<>();
                                fnMap.put("container_count", entry.getValue().size());
                                fnMap.put("container_count_ready", entry.getValue().size());
                                fnMap.put("container_MIPS", (int) entry.getValue().get(0).getMips());
                                fnMap.put("container_ram", (int) entry.getValue().get(0).getRam());
                                fnMap.put("container_PES", entry.getValue().get(0).getNumberOfPes());
                                fnMap.put("container_count_pending", 0);
                                globalFunctionContainerMap.put(entry.getKey(), fnMap);
                            }
                        } else {
                            HashMap<String, Integer> fnMap = (HashMap<String, Integer>) globalFunctionContainerMap.get(entry.getKey());
                            fnMap.put("container_count", fnMap.get("container_count") + entry.getValue().size());
                            fnMap.put("container_count_ready", fnMap.get("container_count_ready") + entry.getValue().size());
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        }
                    }
                    for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMapPending().entrySet()) {
                        if (!globalFunctionContainerMap.containsKey(entry.getKey())) {
                            if (!entry.getValue().isEmpty()) {
                                HashMap<String, Integer> fnMap = new HashMap<>();
                                fnMap.put("container_count", entry.getValue().size());
                                fnMap.put("container_count_ready", 0);
                                fnMap.put("container_count_pending", entry.getValue().size());
                                fnMap.put("container_MIPS", (int) entry.getValue().get(0).getMips());
                                fnMap.put("container_ram", (int) entry.getValue().get(0).getRam());
                                fnMap.put("container_PES", entry.getValue().get(0).getNumberOfPes());
                                globalFunctionContainerMap.put(entry.getKey(), fnMap);
                            }
                        } else {
                            HashMap<String, Integer> fnMap = (HashMap<String, Integer>) globalFunctionContainerMap.get(entry.getKey());
                            fnMap.put("container_count_pending", fnMap.get("container_count_pending") + entry.getValue().size());
                            fnMap.put("container_count", fnMap.get("container_count") + entry.getValue().size());
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        }
                    }
                    for (String key: vm.getFunctionContainerMap().keySet()) {
                        if (!functionsNormalizedMetrics.containsKey(key)) {
                            HashMap<String, ArrayList<Integer>> metricsMap = new HashMap<>();
                            metricsMap.put("ema", new ArrayList<>());
                            metricsMap.get("ema").add(vm.getNormalizedEMA(key));
                            metricsMap.put("sma", new ArrayList<>());
                            metricsMap.get("sma").add(vm.getNormalizedSMA(key));
                            functionsNormalizedMetrics.put(key, metricsMap);
                        } else {
                            functionsNormalizedMetrics.get(key).get("ema").add(vm.getNormalizedEMA(key));
                            functionsNormalizedMetrics.get(key).get("sma").add(vm.getNormalizedSMA(key));
                        }
                    }
                }
            }
            for (Map.Entry<String, Map<String, Integer>> entry: globalFunctionContainerMap.entrySet()) {
                int inflights = 0;
                int clusterEMA = 0;
                int clusterSMA = 1;
                if (((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().containsKey(entry.getKey())) {
                    if (functionsNormalizedMetrics.containsKey(entry.getKey())) {
                       ArrayList<Integer> emas = functionsNormalizedMetrics.get(entry.getKey()).get("ema");
                       int sum = 0;
                       for (int ema: emas) {
                          sum += ema;
                       }
                       clusterEMA = sum / functionsNormalizedMetrics.get(entry.getKey()).get("ema").size();

                        ArrayList<Integer> smas = functionsNormalizedMetrics.get(entry.getKey()).get("sma");
                        sum = 0;
                        for (int sma: smas) {
                            sum += sma;
                        }
                        clusterSMA = sum / functionsNormalizedMetrics.get(entry.getKey()).get("sma").size();

                    }
                    inflights = ((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().get(entry.getKey()) - entry.getValue().get("container_count_pending");
                }
                inflights = Math.min(inflights, 0);
                if (((double) (clusterEMA / clusterSMA)) >= 1.0) {
                    int numberOfContainers = (int) Math.ceil((double)(clusterEMA / clusterSMA) * Math.sqrt(inflights)) + inflights;
                    int containerGap = numberOfContainers - entry.getValue().get("container_count");
                    if (containerGap < 0) {
                        containerGap = 0;
                    }
                    for (int i = 0; i < containerGap; i++) {
                        String[] dt = new String[5] ;
                        dt[0] = Integer.toString(userId);
                        dt[1] = entry.getKey();
                        dt[2] = Double.toString(entry.getValue().get("container_MIPS"));
                        dt[3] = Double.toString(entry.getValue().get("container_ram"));
                        dt[4] = Double.toString(entry.getValue().get("container_PES"));

                        getServerlessDatacenter().sendScaledContainerCreationRequest(dt);
                    }

                }
            }
            for (ContainerHost host: hostList) {
                for (ContainerVm machine: host.getVmList()) {
                    MaasServerlessInvoker vm = (MaasServerlessInvoker) machine;
                    for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMap().entrySet()) {
                        for (Container container: entry.getValue()) {
                            ServerlessContainer cont = (ServerlessContainer) container;
                            if (cont.getRunningTasks().isEmpty()) {
                                cont.setIdleStartTime(CloudSim.clock());
                                getServerlessDatacenter().getContainersToDestroy().add(cont);
                            }
                        }
                    }
                }
            }
        }
    }
}
