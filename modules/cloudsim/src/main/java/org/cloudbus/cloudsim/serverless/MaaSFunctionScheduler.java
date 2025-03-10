package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class MaaSFunctionScheduler  extends FunctionScheduler {

    @Override
    public ContainerVm findVmForContainer(Container container) {
        List<MaasServerlessInvoker> invokers = getContainerVmList();
        invokers.sort(new Comparator<MaasServerlessInvoker>() {
            @Override
            public int compare(MaasServerlessInvoker vm1, MaasServerlessInvoker vm2) {
                double util1 = vm1.getTotalUtilizationOfCpu(CloudSim.clock()) / vm1.getNormalizedEMA(((ServerlessContainer) container).getType());
                double util2 = vm2.getTotalUtilizationOfCpu(CloudSim.clock()) / vm2.getNormalizedEMA(((ServerlessContainer) container).getType());
                return Double.compare(util2, util1);
            }
        });
        for (MaasServerlessInvoker invoker : invokers) {
            if (invoker.isSuitableForContainer(container, invoker)) {
                return invoker;
            }
        }
        return null;
    }
}
