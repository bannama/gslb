package com.oneops.gslb.mtd.v2.domain;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class MtdHost {

  @SerializedName("mtd_host_name")
  public abstract String mtdHostName();

  @SerializedName("fallback_target")
  @Nullable
  public abstract String fallbackTarget();

  @SerializedName("mtd_health_checks")
  @Nullable
  public abstract List<MtdHostHealthCheck> mtdHealthChecks();

  @SerializedName("mtd_targets")
  @Nullable
  public abstract List<MtdTarget> mtdTargets();

  @SerializedName("is_dc_failover")
  @Nullable
  public abstract Boolean isDcFailover();

  @SerializedName("load_balancing_distribution")
  @Nullable
  public abstract Integer loadBalancingDistribution();

  @SerializedName("dc_fallback_targets")
  @Nullable
  public abstract List<DCFallbackTarget> dcFallbackTargets();

  @SerializedName("locality_scope")
  @Nullable
  public abstract Integer localityScope();

  public static MtdHost create(String mtdHostName, @Nullable  String fallbackTarget,
      @Nullable List<MtdHostHealthCheck> mtdHostHealthChecks, List<MtdTarget> mtdTargets,
      Boolean isDcFailover, Integer loadBalancingDistribution,
      @Nullable List<DCFallbackTarget> dcFallbackTargets,
      @Nullable Integer localityScope) {
    return new AutoValue_MtdHost(mtdHostName, fallbackTarget, mtdHostHealthChecks,
        mtdTargets, isDcFailover, loadBalancingDistribution, dcFallbackTargets, localityScope);
  }

  public static TypeAdapter<MtdHost> typeAdapter(Gson gson) {
    return new AutoValue_MtdHost.GsonTypeAdapter(gson);
  }
  
}

