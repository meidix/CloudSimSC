package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;

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
}
