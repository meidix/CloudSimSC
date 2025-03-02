package org.cloudbus.cloudsim.serverless;


import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisioner;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerRamProvisioner;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.schedulers.ContainerScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class ServerlessInvokerRequestAware  extends ServerlessInvoker {

    HashMap<String, ArrayList<ServerlessRequest>> finishedTasksMap;

    public ServerlessInvokerRequestAware(int id, int userId, double mips, float ram, long bw, long size, String vmm, ContainerScheduler containerScheduler, ContainerRamProvisioner containerRamProvisioner, ContainerBwProvisioner containerBwProvisioner, List<? extends ContainerPe> peList, double schedulingInterval) {
        super(id, userId, mips, ram, bw, size, vmm, containerScheduler, containerRamProvisioner, containerBwProvisioner, peList, schedulingInterval);
    }

    protected void addToFinishedTaskMap(ServerlessRequest request) {
        finishedTasksMap = new HashMap<>();
        if (finishedTasksMap.containsKey(request.getRequestFunctionId())) {
            finishedTasksMap.get(request.getRequestFunctionId()).add(request);
        } else {
            ArrayList<ServerlessRequest> requestList = new ArrayList<>();
            requestList.add(request);
            finishedTasksMap.put(request.getRequestFunctionId(), requestList);
        }
    }

    protected HashMap<String, ArrayList<ServerlessRequest>> getFinishedTaskMap() {
        for (Container cont: getContainerList()) {
            List<ServerlessRequest> finishedRequests = ((ServerlessContainer) cont).getfinishedTasks();
            for (ServerlessRequest request : finishedRequests) {
                addToFinishedTaskMap(request);
            }
        }
        return finishedTasksMap;
    }
}
