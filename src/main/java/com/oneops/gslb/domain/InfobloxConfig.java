package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class InfobloxConfig {

  public abstract String host();

  public abstract String user();

  public abstract String pwd();

  public abstract String zone();

  public static InfobloxConfig create(String host, String user, String pwd, String zone) {
    return new AutoValue_InfobloxConfig(host, user, pwd, zone);
  }
  
  public static TypeAdapter<InfobloxConfig> typeAdapter(Gson gson) {
    return new AutoValue_InfobloxConfig.GsonTypeAdapter(gson);
  }
  
}
