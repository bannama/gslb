package com.oneops.gslb.domain;

import com.oneops.gslb.Status;
import java.util.HashMap;
import java.util.Map;

public class GslbProvisionResponse extends GslbResponse {

  private Map<String, String> dnsEntries = new HashMap<>();
  private String mtdBaseId;
  private String mtdVersion;
  private String glb;
  private String mtdDeploymentId;

  public static GslbProvisionResponse failedResponse(String message) {
    GslbProvisionResponse response = new GslbProvisionResponse();
    response.status = Status.FAILED;
    response.failureMessage = message;
    return response;
  }

  public Map<String, String> getDnsEntries() {
    return dnsEntries;
  }

  public void setDnsEntries(Map<String, String> dnsEntries) {
    this.dnsEntries = dnsEntries;
  }

  public String getMtdBaseId() {
    return mtdBaseId;
  }

  public void setMtdBaseId(String mtdBaseId) {
    this.mtdBaseId = mtdBaseId;
  }

  public String getMtdVersion() {
    return mtdVersion;
  }

  public void setMtdVersion(String mtdVersion) {
    this.mtdVersion = mtdVersion;
  }

  public String getGlb() {
    return glb;
  }

  public void setGlb(String glb) {
    this.glb = glb;
  }

  public String getMtdDeploymentId() {
    return mtdDeploymentId;
  }

  public void setMtdDeploymentId(String mtdDeploymentId) {
    this.mtdDeploymentId = mtdDeploymentId;
  }

}
