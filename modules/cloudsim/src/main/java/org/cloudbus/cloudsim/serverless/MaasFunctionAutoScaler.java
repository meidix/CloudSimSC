package org.cloudbus.cloudsim.serverless;

import org.apache.commons.lang3.tuple.Pair;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerHost;
import org.cloudbus.cloudsim.container.core.ContainerVm;

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
        if (Constants.FUNCTION_HORIZONTAL_AUTOSCALING) {
            ArrayList<MaasServerlessInvoker> usedVms = new ArrayList<>();
            List<? extends ContainerHost> hostList = getServerlessDatacenter().getVmAllocationPolicy().getContainerHostList();
            for (ContainerHost host : hostList) {
                for (ContainerVm machine : host.getVmList()) {
                    MaasServerlessInvoker vm = (MaasServerlessInvoker) machine;
                    if (!vm.getFunctionContainerMap().isEmpty()) {
                        usedVms.add(vm);
                    }
                }
            }

            HashMap<String, HashMap<String, ArrayList<Integer>>> functionSituation = new HashMap<>();
            for (MaasServerlessInvoker vm : usedVms) {
                for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMap().entrySet()) {
                    if (!functionSituation.containsKey(entry.getKey())) {
                        functionSituation.put(entry.getKey(), new HashMap<>());
                        functionSituation.get(entry.getKey()).put("ema", new ArrayList<>());
                        functionSituation.get(entry.getKey()).put("sma", new ArrayList<>());
                    }
                    functionSituation.get(entry.getKey()).get("ema").add(vm.getNormalizedEMA(entry.getKey()));
                    functionSituation.get(entry.getKey()).get("sma").add(vm.getNormalizedSMA(entry.getKey()));
                }
            }

            for (Map.Entry<String, HashMap<String, ArrayList<Integer>>> entry: functionSituation.entrySet()) {
                int clusterEMA = 0;
                if (entry.getValue().get("ema").size() % 2 == 0) {
                    int index = entry.getValue().get("ema").size() / 2;
                    clusterEMA = (entry.getValue().get("ema").get(index - 1) + entry.getValue().get("ema").get(index - 1)) / 2;
                } else {
                    clusterEMA = entry.getValue().get("ema").get(entry.getValue().get("ema").size() / 2) ;
                }

                int clusterSMA = 0;
                if (entry.getValue().get("sma").size() % 2 == 0) {
                    int index = entry.getValue().get("sma").size() / 2;
                    clusterSMA = (entry.getValue().get("sma").get(index - 1) + entry.getValue().get("sma").get(index - 1)) / 2;
                } else {
                    clusterSMA = entry.getValue().get("sma").get(entry.getValue().get("sma").size() / 2) ;
                }
                if (clusterEMA > clusterSMA) {
                    // scale out
                    if (clusterEMA == 2) {
                        if (((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().containsKey(entry.getKey())) {
                            int inflights = ((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().get(entry.getKey());
                            int numContainers = (int) Math.floor(Math.sqrt(inflights));
                        }
                    }
                } else {
                    // scale down
                }
            }

        }
    }


}
