package com.oneops.gslb;

import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.ProvisionedGslb;
import java.util.List;

public class GslbProvider {

  private MtdHandler mtdHandler = new MtdHandler();

  private DnsHandler dnsHandler = new DnsHandler();

  public GslbProvisionResponse create(Gslb gslb) {
    ProvisionContext context = initializeProvisionContext(gslb.logContextId());
    mtdHandler.setupTorbitGslb(gslb, context);
    if (isNotFailed(context) && areDnsEntriesNeeded(gslb)) {
      dnsHandler.setupDnsEntries(gslb, context);
    }
    updateResponseStatus(context.getProvisioningResponse());
    return context.getProvisioningResponse();
  }

  public GslbResponse delete(ProvisionedGslb provisionedGslb) {
    Context context = initializeContext(provisionedGslb.logContextId());
    mtdHandler.deleteGslb(provisionedGslb, context);
    if (isNotFailed(context) && areDnsEntriesNeeded(provisionedGslb)) {
      dnsHandler.removeDnsEntries(provisionedGslb, context);
    }
    updateResponseStatus(context.getResponse());
    return context.getResponse();
  }

  public GslbProvisionResponse checkStatus(Gslb gslb) {
    ProvisionContext context = initializeProvisionContext(gslb.logContextId());
    mtdHandler.checkStatus(gslb, context);
    if (isNotFailed(context) && areDnsEntriesNeeded(gslb)) {
      dnsHandler.checkStatus(gslb, context);
    }
    updateResponseStatus(context.getProvisioningResponse());
    return context.getProvisioningResponse();
  }

  private boolean isNotFailed(Context context) {
    return context.getResponse().getStatus() != Status.FAILED;
  }

  private boolean areDnsEntriesNeeded(Gslb gslb) {
    return (isNotEmpty(gslb.cnames()) ||
        isNotEmpty(gslb.cloudARecords()) ||
        isNotEmpty(gslb.obsoleteCnames()) ||
        isNotEmpty(gslb.obsoleteCloudARecords()));
  }

  private boolean areDnsEntriesNeeded(ProvisionedGslb gslb) {
    return (isNotEmpty(gslb.cnames()) ||
        isNotEmpty(gslb.cloudARecords()));
  }

  private boolean isNotEmpty(List<?> list) {
    return (list != null && !list.isEmpty());
  }

  private ProvisionContext initializeProvisionContext(String logKey) {
    ProvisionContext context = new ProvisionContext();
    context.setProvisioningResponse(new GslbProvisionResponse());
    context.logKey(logKey);
    return context;
  }

  private Context initializeContext(String logKey) {
    Context context = new Context();
    context.setResponse(new GslbResponse());
    context.logKey(logKey);
    return context;
  }

  private void updateResponseStatus(GslbResponse response) {
    if (response.getStatus() != Status.FAILED) {
      response.setStatus(Status.SUCCESS);
    }
  }

}
