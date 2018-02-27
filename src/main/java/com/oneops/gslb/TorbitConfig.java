package com.oneops.gslb;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class TorbitConfig {

  public abstract String url();

  public abstract String user();

  public abstract String authKey();

  public abstract int groupId();

  public abstract String gslbBaseDomain();

  public static TorbitConfig create(String url, String user, String authKey, int groupId, String gslbBaseDomain) {
    return new AutoValue_TorbitConfig(url, user, authKey, groupId, gslbBaseDomain);
  }
  
  public static TypeAdapter<TorbitConfig> typeAdapter(Gson gson) {
    return new AutoValue_TorbitConfig.GsonTypeAdapter(gson);
  }
  
}
