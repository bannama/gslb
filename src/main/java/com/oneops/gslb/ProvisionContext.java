package com.oneops.gslb;

import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import java.util.List;

public class ProvisionContext extends Context {

  private GslbProvisionResponse provisioningResponse;
  private List<String> primaryTargets;

  public void failedResponseWithMessage(String message) {
    provisioningResponse = GslbProvisionResponse.failedResponse(message);
  }

  public List<String> getPrimaryTargets() {
    return primaryTargets;
  }

  public void setPrimaryTargets(List<String> primaryTargets) {
    this.primaryTargets = primaryTargets;
  }

  public GslbProvisionResponse getProvisioningResponse() {
    return provisioningResponse;
  }

  public void setProvisioningResponse(GslbProvisionResponse provisioningResponse) {
    this.provisioningResponse = provisioningResponse;
  }

  public GslbResponse getResponse() {
    return provisioningResponse;
  }

}
