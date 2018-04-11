package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class ProvisionedGslb {

  public abstract String app();

  public abstract String subdomain();

  public abstract TorbitConfig torbitConfig();

  @Nullable
  public abstract InfobloxConfig infobloxConfig();

  @Nullable
  public abstract List<String> cnames();

  @Nullable
  public abstract List<CloudARecord> cloudARecords();

  @Nullable
  public abstract String logContextId();

  public static Builder builder() {
    return new AutoValue_ProvisionedGslb.Builder().logContextId("");
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder app(String app);
    public abstract Builder subdomain(String subdomain);
    public abstract Builder torbitConfig(TorbitConfig torbitConfig);
    public abstract Builder infobloxConfig(InfobloxConfig infobloxConfig);
    public abstract Builder cnames(List<String> cnames);
    public abstract Builder cloudARecords(List<CloudARecord> cloudARecords);
    public abstract Builder logContextId(String logContextId);

    public abstract ProvisionedGslb build();
  }

}
