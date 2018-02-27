package com.oneops.gslb;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import javax.annotation.Nullable;

@AutoValue
public abstract class Cloud {

  public abstract long id();

  public abstract String name();

  @Nullable
  public abstract String priority();

  @Nullable
  public abstract String adminStatus4Platform();

  @Nullable
  public abstract TorbitConfig torbitConfig();

  @Nullable
  public abstract InfobloxConfig infobloxConfig();

  public static Cloud create(long id, String name, String priority, String adminStatus4Platform,
      TorbitConfig torbitConfig, InfobloxConfig infobloxConfig) {
    return new AutoValue_Cloud(id, name, priority, adminStatus4Platform, torbitConfig, infobloxConfig);
  }
  
  public static TypeAdapter<Cloud> typeAdapter(Gson gson) {
    return new AutoValue_Cloud.GsonTypeAdapter(gson);
  }

}
