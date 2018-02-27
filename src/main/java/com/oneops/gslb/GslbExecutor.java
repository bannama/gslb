package com.oneops.gslb;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GslbExecutor {

  @Autowired
  MtdHandler mtdHandler;

  @Autowired
  DnsHandler dnsHandler;

  public GslbResponse execute(GslbRequest request) {
    Context context = initializeContext(request);
    mtdHandler.setupTorbitGdns(context);
    if (context.getResponse().getStatus() != Status.FAILED) {
      dnsHandler.setupDnsEntries(context);
      updateResponseStatus(context);
    }
    return context.getResponse();
  }

  private Context initializeContext(GslbRequest request) {
    Context context = new Context(request);
    String subDomain = StringUtils.isNotBlank(request.customSubdomain()) ? request.customSubdomain() :
        String.join(".", request.environment(), request.assembly(), request.org());
    context.setSubDomain(subDomain);
    context.setResponse(new GslbResponse());
    return context;
  }

  private void updateResponseStatus(Context context) {
    if (context.getResponse().getStatus() != Status.FAILED) {
      context.getResponse().setStatus(Status.SUCCESS);
    }
  }

}
