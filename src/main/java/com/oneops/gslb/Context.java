package com.oneops.gslb;

import java.util.List;

public class Context {

  private GslbRequest request;
  private String mtdBaseHost;
  private TorbitClient torbitClient;
  private TorbitApi torbitApi;
  private List<String> primaryTargets;
  private Long cloudId;
  private String subDomain;

  private GslbResponse response;

  public Context(GslbRequest request) {
    this.request = request;
  }

  public GslbRequest getRequest() {
    return request;
  }

  public void setRequest(GslbRequest request) {
    this.request = request;
  }

  public String getMtdBaseHost() {
    return mtdBaseHost;
  }

  public void setMtdBaseHost(String mtdBaseHost) {
    this.mtdBaseHost = mtdBaseHost;
  }

  public TorbitClient getTorbitClient() {
    return torbitClient;
  }

  public void setTorbitClient(TorbitClient torbitClient) {
    this.torbitClient = torbitClient;
  }

  public TorbitApi getTorbitApi() {
    return torbitApi;
  }

  public void setTorbitApi(TorbitApi torbitApi) {
    this.torbitApi = torbitApi;
  }

  public List<String> getPrimaryTargets() {
    return primaryTargets;
  }

  public void setPrimaryTargets(List<String> primaryTargets) {
    this.primaryTargets = primaryTargets;
  }

  public GslbResponse getResponse() {
    return response;
  }

  public void setResponse(GslbResponse response) {
    this.response = response;
  }

  public String logKey() {
    return request.logContextId();
  }

  public String platform() {
    return request.platform();
  }

  public TorbitConfig torbitConfig() {
    return request.cloud().torbitConfig();
  }

  public InfobloxConfig infobloxConfig() {
    return request.cloud().infobloxConfig();
  }

  public Cloud cloud() {
    return request.cloud();
  }

  public Long getCloudId() {
    return cloudId;
  }

  public void setCloudId(Long cloudId) {
    this.cloudId = cloudId;
  }

  public String getSubDomain() {
    return subDomain;
  }

  public void setSubDomain(String subDomain) {
    this.subDomain = subDomain;
  }
}
