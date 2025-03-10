package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Log;

public class MaasServerlessRequestScheduler extends ServerlessRequestScheduler {

    public MaasServerlessRequestScheduler(double mips, int numberOfPes) {
        super(mips, numberOfPes);
    }

    @Override
    public boolean isSuitableForRequest(ServerlessRequest cl, ServerlessContainer cont) {
        Log.printLine(String.format("Current allocated ram of cont #%s is #%s and requested ram of cl #%s is %s", cont.getId(), getTotalCurrentAllocatedRamForRequests(), cl.getCloudletId(), cl.getContainerMemory()*cl.getUtilizationOfRam()));
        return (cl.getContainerMemory() * cl.getUtilizationOfRam() <= (cont.getRam() - getTotalCurrentAllocatedRamForRequests())) && (cl.getNumberOfPes() <= getNumberOfPes());
    }
}
