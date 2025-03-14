package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class EnsureServerlessController  extends ServerlessController {


    public EnsureServerlessController(String name, int overBookingfactor) throws Exception {
        super(name, overBookingfactor);
    }

    protected void createContainer(ServerlessRequest cl, String requestId, int brokerId, int vmId) {
        ServerlessContainer container = new ServerlessContainer(containerId, brokerId, requestId, cl.getContainerMIPS(), cl.getNumberOfPes(), cl.getContainerMemory(), Constants.CONTAINER_BW, Constants.CONTAINER_SIZE,"Xen", new ServerlessRequestScheduler(cl.getContainerMIPS(), cl.getNumberOfPes()), Constants.SCHEDULING_INTERVAL, true, false, false, 0, 0, 0);
        ContainerVm vm = ContainerVmList.getById(getVmsCreatedList(), vmId);
        assert vm != null;
        container.setVm(vm);
        getContainerList().add(container);
        if (!(cl ==null)){
            cl.setContainerId(containerId);
        }

        submitContainer(cl, container);
        containerId++;
    }

    public int getMaximumVmCount() {
        ArrayList<Integer> vmUsedList = new ArrayList<>();
        for (Container container : getContainerList()) {
            ServerlessContainer cont = (ServerlessContainer) container;
            vmUsedList.add(cont.getVm().getId());
        }

        Set<Integer> result = new HashSet<>(vmUsedList);
        return result.size();
    }

    public int getSloViolationCount(HashMap<String, Double> isoResponseTimes) {
        int violations = 0;
        double responseThreshold = 0;
        for (Container container : getContainerList()) {
            ServerlessContainer cont = (ServerlessContainer) container;
            if (isoResponseTimes.containsKey(cont.getType())) {
                responseThreshold = isoResponseTimes.get(cont.getType()) * Constants.ENSURE_LATENCY_THRESHOLD;
            }
            if (responseThreshold > 0) {
                for (ServerlessRequest request: cont.getfinishedTasks()) {
                    if ((request.getFinishTime() - request.getArrivalTime()) > responseThreshold) {
                        violations++;
                    }
                }
            }
        }
        return violations;
    }
}
