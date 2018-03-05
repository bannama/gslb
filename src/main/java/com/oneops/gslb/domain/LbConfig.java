package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class LbConfig {

  public abstract String listenerJson();
  public abstract String ecvMapJson();

  public static LbConfig create(String listenerJson, String ecvMapJson) {
    return new AutoValue_LbConfig(listenerJson, ecvMapJson);
  }
  
  public static TypeAdapter<LbConfig> typeAdapter(Gson gson) {
    return new AutoValue_LbConfig.GsonTypeAdapter(gson);
  }
}
