package com.oneops.gslb;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class DeployedLb {

  public abstract String name();
  public abstract String dnsRecord();
  
  public static DeployedLb create(String name, String dnsRecord) {
    return new AutoValue_DeployedLb(name, dnsRecord);
  }
  
  public static TypeAdapter<DeployedLb> typeAdapter(Gson gson) {
    return new AutoValue_DeployedLb.GsonTypeAdapter(gson);
  }

}
