package com.oneops.gslb.domain;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class HealthCheck {

  public abstract Protocol protocol();

  public abstract int port();

  @Nullable
  public abstract String path();

  @Nullable
  public abstract Integer intervalInSecs();

  @Nullable
  public abstract Integer timeoutInSecs();

  @Nullable
  public abstract Integer retryDelayInSecs();

  @Nullable
  public abstract Integer failureCountToMarkDown();

  public static Builder builder() {
    Builder builder = new AutoValue_HealthCheck.Builder()
        .intervalInSecs(5)
        .timeoutInSecs(2)
        .retryDelayInSecs(30)
        .failureCountToMarkDown(3);
    return builder;
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder protocol(Protocol protocol);
    public abstract Builder port(int port);
    public abstract Builder path(String path);
    public abstract Builder intervalInSecs(Integer intervalInSecs);
    public abstract Builder timeoutInSecs(Integer timeoutInSecs);
    public abstract Builder retryDelayInSecs(Integer retryDelayInSecs);
    public abstract Builder failureCountToMarkDown(Integer failureCountToMarkDown);

    public abstract HealthCheck build();
  }




}
