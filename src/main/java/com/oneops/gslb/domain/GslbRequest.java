package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class GslbRequest {

  public abstract Action action();

  public abstract Fqdn fqdn();

  @Nullable
  public abstract Fqdn oldFqdn();

  public abstract String platform();

  public abstract String environment();

  public abstract String assembly();

  public abstract String org();

  public abstract Cloud cloud();

  public abstract boolean platformEnabled();

  @Nullable
  public abstract String customSubdomain();

  public abstract LbConfig lbConfig();

  public abstract List<DeployedLb> deployedLbs();

  public abstract List<Cloud> platformClouds();

  @Nullable
  public abstract String logContextId();

  public static Builder builder() {
    return new AutoValue_GslbRequest.Builder().logContextId("");
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder action(Action action);
    public abstract Builder fqdn(Fqdn fqdn);
    public abstract Builder oldFqdn(Fqdn oldFqdn);
    public abstract Builder platform(String platform);
    public abstract Builder environment(String environemnt);
    public abstract Builder assembly(String assembly);
    public abstract Builder org(String org);
    public abstract Builder cloud(Cloud cloud);
    public abstract Builder platformEnabled(boolean platformEnabled);
    public abstract Builder customSubdomain(String customSubdomain);
    public abstract Builder lbConfig(LbConfig lbConfig);
    public abstract Builder deployedLbs(List<DeployedLb> deployedLbs);
    public abstract Builder platformClouds(List<Cloud> platformClouds);
    public abstract Builder logContextId(String logContextId);

    public abstract GslbRequest build();

  }

}
