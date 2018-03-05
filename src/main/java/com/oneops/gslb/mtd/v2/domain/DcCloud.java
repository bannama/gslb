package com.oneops.gslb.mtd.v2.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class DcCloud {

  @SerializedName("id")
  public abstract Integer id();

  @SerializedName("name")
  public abstract String name();

  @SerializedName("data_center_id")
  public abstract Integer dataCenterId();

  @SerializedName("cidrs")
  @Nullable
  public abstract List<String> cidrs();

  public static DcCloud create(Integer id, String name, Integer dataCenterId, @Nullable List<String> cidrs) {
    return new AutoValue_DcCloud(id, name, dataCenterId, cidrs);
  }

  public static TypeAdapter<DcCloud> typeAdapter(Gson gson) {
    return new AutoValue_DcCloud.GsonTypeAdapter(gson);
  }

}

