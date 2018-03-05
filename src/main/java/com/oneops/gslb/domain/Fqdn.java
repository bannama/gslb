package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import javax.annotation.Nullable;

@AutoValue
public abstract class Fqdn {

  @Nullable
  public abstract String aliasesJson();

  @Nullable
  public abstract String fullAliasesJson();

  @Nullable
  public abstract String distribution();

  public static Fqdn create(String aliasesJson, String fullAliasesJson, String distribution) {
    return new AutoValue_Fqdn(aliasesJson, fullAliasesJson, distribution);
  }
  
  public static TypeAdapter<Fqdn> typeAdapter(Gson gson) {
    return new AutoValue_Fqdn.GsonTypeAdapter(gson);
  }

}
