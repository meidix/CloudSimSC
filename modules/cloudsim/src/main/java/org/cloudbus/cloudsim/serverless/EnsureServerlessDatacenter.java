package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.container.core.*;
import org.cloudbus.cloudsim.container.resourceAllocators.ContainerVmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

public class EnsureServerlessDatacenter extends ServerlessDatacenter {

    private HashMap<String, Integer> functionInflights;

    /**
     * Allocates a new PowerDatacenter object.
     *
     * @param name
     * @param characteristics
     * @param vmAllocationPolicy
     * @param containerAllocationPolicy
     * @param storageList
     * @param schedulingInterval
     * @param experimentName
     * @param logAddress
     * @param vmStartupDelay
     * @param containerStartupDelay
     * @param monitor
     * @throws Exception
     */
    public EnsureServerlessDatacenter(String name, ContainerDatacenterCharacteristics characteristics, ContainerVmAllocationPolicy vmAllocationPolicy, FunctionScheduler containerAllocationPolicy, List<Storage> storageList, double schedulingInterval, String experimentName, String logAddress, double vmStartupDelay, double containerStartupDelay, boolean monitor) throws Exception {
        super(name, characteristics, vmAllocationPolicy, containerAllocationPolicy, storageList, schedulingInterval, experimentName, logAddress, vmStartupDelay, containerStartupDelay, monitor);
        functionInflights = new HashMap<>();
    }


    @Override
    public void checkCloudletCompletion() {
        List<? extends ContainerHost> list = getVmAllocationPolicy().getContainerHostList();
        for (int i = 0; i < list.size(); i++) {
            ContainerHost host = list.get(i);
            for (ContainerVm vm : host.getVmList()) {
                for (Container container : vm.getContainerList()) {
                    while (container.getContainerCloudletScheduler().isFinishedCloudlets()) {
                        Cloudlet cl = container.getContainerCloudletScheduler().getNextFinishedCloudlet();
                        if (cl != null) {
                            Map.Entry<Cloudlet, ContainerVm> data =  new AbstractMap.SimpleEntry<>(cl, vm);
                            for(int x=0; x<((ServerlessInvoker)vm).getRunningRequestList().size();x++){
                                if(((ServerlessInvoker)vm).getRunningRequestList().get(x)==(ServerlessRequest)cl){
                                    ((ServerlessInvoker)vm).getRunningRequestList().remove(x);
                                }
                            }
                            ((ServerlessInvokerRequestAware) vm).addToFinishedTaskMap((ServerlessRequest)cl);
                            decrementFunctionInflightRequests(((ServerlessRequest) cl).getRequestFunctionId());
                            sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, data);
                        }
                    }
                }
            }
        }
    }

    protected void decrementFunctionInflightRequests(String requestFunctionId) {
       if (functionInflights.containsKey(requestFunctionId)) {
           int current = functionInflights.remove(requestFunctionId);
           current -= 1;
           functionInflights.put(requestFunctionId, current);
       } else {
           functionInflights.put(requestFunctionId, 0);
       }
    }

    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        super.processCloudletSubmit(ev, ack);
        ServerlessRequest request = (ServerlessRequest) ev.getData();
        if (!request.isFinished()) {
            incrementFunctionInflightRequests(request.getRequestFunctionId());
        }
    }

    protected void incrementFunctionInflightRequests(String requestFunctionId) {
        if (functionInflights.containsKey(requestFunctionId)) {
            int current = functionInflights.remove(requestFunctionId);
            current += 1;
            functionInflights.put(requestFunctionId, current);
        } else {
            functionInflights.put(requestFunctionId, 1);
        }
    }

    public HashMap<String, Integer> getFunctionInflights() {
        return functionInflights;
    }

}
