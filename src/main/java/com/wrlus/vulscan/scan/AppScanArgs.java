package com.wrlus.vulscan.scan;

import com.wrlus.vulscan.common.ScanArgs;

public class AppScanArgs extends ScanArgs {
    public boolean remote;

    public AppScanArgs() {
        super();
        this.remote = false;
    }

}
