package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class CloudARecord {

  public abstract String cloud();
  public abstract String aRecord();

  public static CloudARecord create(String cloud, String aRecord) {
    return new AutoValue_CloudARecord(cloud, aRecord);
  }

  public static TypeAdapter<CloudARecord> typeAdapter(Gson gson) {
    return new AutoValue_CloudARecord.GsonTypeAdapter(gson);
  }
 }
