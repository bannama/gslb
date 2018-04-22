package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import javax.annotation.Nullable;

@AutoValue
public abstract class Lb {

  public abstract String cloud();
  public abstract String vip();
  public abstract boolean enabledForTraffic();

  @Nullable
  public abstract Integer weightPercent();

  public static Lb create(String cloud, String vip, boolean enabledForTraffic, Integer weightPercent) {
    return new AutoValue_Lb(cloud, vip, enabledForTraffic, weightPercent);
  }

  public static TypeAdapter<Lb> typeAdapter(Gson gson) {
    return new AutoValue_Lb.GsonTypeAdapter(gson);
  }

}
