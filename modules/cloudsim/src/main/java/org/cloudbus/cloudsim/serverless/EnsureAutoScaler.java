package org.cloudbus.cloudsim.serverless;


import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerHost;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnsureAutoScaler extends FunctionAutoScaler {
    public EnsureAutoScaler(ServerlessDatacenter dc) {
        super(dc);
    }

    @Override
    public void scaleFunctions() {
        int userId = 0;
        if (Constants.FUNCTION_VERTICAL_AUTOSCALING) {
            List<? extends ContainerHost> hostList = getServerlessDatacenter().getVmAllocationPolicy().getContainerHostList();
            for (ContainerHost host: hostList) {
                for (ContainerVm machine: host.getVmList()) {
                   EnsureServerlessInvoker vm = (EnsureServerlessInvoker) machine;
                   for (Map.Entry<String, ArrayList<ServerlessRequest>> entry: vm.getFinishedTaskMap().entrySet()) {
                       if (vm.getFunctionContainerMap().containsKey(entry.getKey())) {
                           double latencyAverage = vm.getFunctionLatencyMovingAverage(entry.getKey());
                           double allowableLatency = vm.getFunctionAllowableLatency(entry.getKey());
                           if (latencyAverage > allowableLatency) {
                                // TODO: adjust cpu shares
                           }
                       }
                   }
                }
           }
        }
        if (Constants.FUNCTION_HORIZONTAL_AUTOSCALING) {
            HashMap<String, Map<String, Integer>> globalFunctionContainerMap  = new HashMap<>();
            List<? extends ContainerHost> hostList = getServerlessDatacenter().getVmAllocationPolicy().getContainerHostList();
            for (ContainerHost host: hostList) {
                for (ContainerVm machine: host.getVmList()) {
                    userId = machine.getUserId();
                    EnsureServerlessInvoker vm = (EnsureServerlessInvoker) machine;
                    for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMap().entrySet()) {
                        if (!globalFunctionContainerMap.containsKey(entry.getKey())) {
                            HashMap<String, Integer> fnMap = new HashMap<>();
                            fnMap.put("container_count", entry.getValue().size());
                            fnMap.put("container_count_ready", entry.getValue().size());
                            fnMap.put("container_MIPS", (int) entry.getValue().get(0).getMips());
                            fnMap.put("container_ram", (int) entry.getValue().get(0).getRam());
                            fnMap.put("container_PES", entry.getValue().get(0).getNumberOfPes());
                            fnMap.put("container_count_pending", 0);
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        } else {
                            HashMap<String, Integer> fnMap = (HashMap<String, Integer>) globalFunctionContainerMap.get(entry.getKey());
                            fnMap.put("container_count", fnMap.get("container_count") + entry.getValue().size());
                            fnMap.put("container_count_ready", fnMap.get("container_count_ready") + entry.getValue().size());
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        }
                    }
                    for (Map.Entry<String, ArrayList<Container>> entry: vm.getFunctionContainerMapPending().entrySet()) {
                        if (!globalFunctionContainerMap.containsKey(entry.getKey())) {
                            HashMap<String, Integer> fnMap = new HashMap<>();
                            fnMap.put("container_count", entry.getValue().size());
                            fnMap.put("container_count_ready", 0);
                            fnMap.put("container_count_pending", entry.getValue().size());
                            fnMap.put("container_MIPS", (int) entry.getValue().get(0).getMips());
                            fnMap.put("container_ram", (int) entry.getValue().get(0).getRam());
                            fnMap.put("container_PES", entry.getValue().get(0).getNumberOfPes());
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        } else {
                            HashMap<String, Integer> fnMap = (HashMap<String, Integer>) globalFunctionContainerMap.get(entry.getKey());
                            fnMap.put("container_count_pending", fnMap.get("container_count_pending") + entry.getValue().size());
                            fnMap.put("container_count", fnMap.get("container_count") + entry.getValue().size());
                            globalFunctionContainerMap.put(entry.getKey(), fnMap);
                        }
                    }
                }
            }
            for (Map.Entry<String, Map<String, Integer>> entry: globalFunctionContainerMap.entrySet()) {
                int inflights = 0;
                if (((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().containsKey(entry.getKey())) {
                    inflights = ((EnsureServerlessDatacenter) getServerlessDatacenter()).getFunctionInflights().get(entry.getKey());
                }
                int numberOfContainers = (int) Math.floor(Math.sqrt(inflights)) + inflights;
                int containerGap = numberOfContainers - entry.getValue().get("container_count");
                if (containerGap > 0) {
                    for (int i = 0; i < containerGap; i++) {
                        String[] dt = new String[5] ;
                        dt[0] = Integer.toString(userId);
                        dt[1] = entry.getKey();
                        dt[2] = Double.toString(entry.getValue().get("container_MIPS"));
                        dt[3] = Double.toString(entry.getValue().get("container_ram"));
                        dt[4] = Double.toString(entry.getValue().get("container_PES"));

                        getServerlessDatacenter().sendScaledContainerCreationRequest(dt);
                    }
                } else {
                    hostList = getServerlessDatacenter().getVmAllocationPolicy().getContainerHostList();
                    outter:
                    for (int i = hostList.size() - 1; i >= 0; i--) {
                        ContainerHost host = hostList.get(i);
                        for (int j = host.getVmList().size() - 1; j >= 0; j--) {
                            EnsureServerlessInvoker vm = (EnsureServerlessInvoker) host.getVmList().get(j);
                            if (vm.getFunctionContainerMap().containsKey(entry.getKey())) {
                                for (Container cont: vm.getFunctionContainerMap().get(entry.getKey())) {
                                    ServerlessContainer container = (ServerlessContainer) cont;
                                    if (container.getIdling() || container.getRunningTasks().isEmpty()) {
                                        container.setIdleStartTime(CloudSim.clock());
                                        getServerlessDatacenter().getContainersToDestroy().add(container);
                                        containerGap++;
                                        if (containerGap == 0) {
                                            break outter;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                outter:
                for (ContainerHost host: hostList) {
                    for (ContainerVm machine: host.getVmList()) {
                        EnsureServerlessInvoker vm = (EnsureServerlessInvoker) machine;
                        if (vm.getFunctionContainerMap().containsKey(entry.getKey())) {
                            for (Container cont: vm.getFunctionContainerMap().get(entry.getKey())) {
                                ServerlessContainer container = (ServerlessContainer) cont;
                                if (container.getIdling() || container.getRunningTasks().isEmpty()) {
                                    if (numberOfContainers == 0) {
                                        break outter;
                                    }
                                    container.setIdleStartTime(0);
                                    numberOfContainers--;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
