package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;

public class EnsureFunctionScheduler  extends FunctionScheduler {

    @Override
    public ContainerVm findVmForContainer(Container container) {
        EnsureServerlessInvoker selectedVm = null;
        for (int x = 1; x <= getContainerVmList().size(); x++) {
            EnsureServerlessInvoker tempSelectedVm = (EnsureServerlessInvoker) (ContainerVmList.getById(getContainerVmList(), x));
            assert tempSelectedVm != null;
            if (tempSelectedVm.isSuitableForContainer(container, tempSelectedVm)) {
                selectedVm = tempSelectedVm;
                break;
            }
        }
        return selectedVm;
    }

}
