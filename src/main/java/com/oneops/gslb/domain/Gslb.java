package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class Gslb {

  public abstract String app();

  public abstract String subdomain();

  public abstract List<Lb> lbs();

  public abstract Distribution distribution();

  public abstract TorbitConfig torbitConfig();

  @Nullable
  public abstract InfobloxConfig infobloxConfig();

  @Nullable
  public abstract List<String> cnames();

  @Nullable
  public abstract List<CloudARecord> cloudARecords();

  @Nullable
  public abstract List<String> obsoleteCnames();

  @Nullable
  public abstract List<CloudARecord> obsoleteCloudARecords();

  @Nullable
  public abstract String logContextId();

  @Nullable
  public abstract List<HealthCheck> healthChecks();

  public static Builder builder() {
    return new AutoValue_Gslb.Builder().logContextId("");
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder app(String app);
    public abstract Builder subdomain(String subdomain);
    public abstract Builder lbs(List<Lb> lbs);
    public abstract Builder distribution(Distribution distribution);
    public abstract Builder torbitConfig(TorbitConfig torbitConfig);
    public abstract Builder infobloxConfig(InfobloxConfig infobloxConfig);
    public abstract Builder cnames(List<String> cnames);
    public abstract Builder cloudARecords(List<CloudARecord> cloudARecords);
    public abstract Builder obsoleteCnames(List<String> obsoleteCnames);
    public abstract Builder obsoleteCloudARecords(List<CloudARecord> obsoleteCloudARecords);
    public abstract Builder logContextId(String logContextId);
    public abstract Builder healthChecks(List<HealthCheck> healthChecks);

    public abstract Gslb build();

  }

}
