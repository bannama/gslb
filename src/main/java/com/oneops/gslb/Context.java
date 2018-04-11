package com.oneops.gslb;

import com.oneops.gslb.domain.GslbResponse;

public class Context {

  protected String mtdBaseName;
  protected String app;
  protected TorbitClient torbitClient;
  protected TorbitApi torbitApi;
  private String logKey;
  private GslbResponse response;

  public void failedResponseWithMessage(String message) {
    response = GslbResponse.failedResponse(message);
  }

  public String getApp() {
    return app;
  }

  public void setApp(String app) {
    this.app = app;
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

  public String logKey() {
    return logKey;
  }

  public void logKey(String logKey) {
    this.logKey = logKey;
  }

  public GslbResponse getResponse() {
    return response;
  }

  public void setResponse(GslbResponse response) {
    this.response = response;
  }

  public String getMtdBaseName() {
    return mtdBaseName;
  }

  public void setMtdBaseName(String mtdBaseName) {
    this.mtdBaseName = mtdBaseName;
  }

}
