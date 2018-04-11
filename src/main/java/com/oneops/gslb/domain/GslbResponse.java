package com.oneops.gslb.domain;

import com.oneops.gslb.Status;

public class GslbResponse {

  protected Status status;
  protected String failureMessage;

  public static GslbResponse failedResponse(String message) {
    GslbResponse response = new GslbResponse();
    response.status = Status.FAILED;
    response.failureMessage = message;
    return response;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
  }

}
