package org.cloudbus.cloudsim.serverless;

public class EnsureAutoScaler extends FunctionAutoScaler {
    public EnsureAutoScaler(ServerlessDatacenter dc) {
        super(dc);
    }

    @Override
    public void scaleFunctions() {}

}
