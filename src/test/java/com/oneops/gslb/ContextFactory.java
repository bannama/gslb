package com.oneops.gslb;

import com.oneops.gslb.domain.Action;
import com.oneops.gslb.domain.Cloud;
import com.oneops.gslb.domain.DeployedLb;
import com.oneops.gslb.domain.Fqdn;
import com.oneops.gslb.domain.GslbRequest;
import com.oneops.gslb.domain.GslbRequest.Builder;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.LbConfig;
import com.oneops.gslb.domain.TorbitConfig;
import java.util.ArrayList;
import java.util.List;

public class ContextFactory {

  public static Context getContext(Action action, String platform, String mtdBaseHost, String subDomian, String cloud,
      Fqdn fqdn, Fqdn oldFqdn, String lbVip, String zone, boolean platformEnabled, InfobloxConfig infobloxConfig) {
    Builder builder = GslbRequest.builder();
    builder.platform(platform).assembly("combo1").environment("stg").org("org1").action(action).platformEnabled(platformEnabled);
    builder.fqdn(fqdn);
    builder.oldFqdn(oldFqdn);
    builder.lbConfig(LbConfig.create("['http 80 http 80']",  "{'80':'GET /'}"));
    builder.logContextId("");

    List<DeployedLb> deployedLbs = new ArrayList<>();
    deployedLbs.add(DeployedLb.create("lb-101-1", lbVip));
    deployedLbs.add(DeployedLb.create("lb-101-2", "1.1.1.1"));
    builder.deployedLbs(deployedLbs);


    TorbitConfig torbitConfig = TorbitConfig.create("https://localhost:8443", "test-oo",
        "test_auth", 101, "glb.xyz.com");
    if (infobloxConfig == null)
      infobloxConfig = InfobloxConfig.create("https://localhost:8121",
        "test-oo", "test_pwd", zone);
    List<Cloud> clouds = new ArrayList<>();

    clouds.add(Cloud.create(101, cloud, "1", "active", null, null));
    clouds.add(Cloud.create(102, "dummyCloud", "1", "active", null, null));
    builder.platformClouds(clouds);
    builder.cloud(Cloud.create(101, cloud, "1", "active", torbitConfig, infobloxConfig));

    Context context = new Context(builder.build());
    context.setSubDomain(subDomian);
    context.setMtdBaseHost(mtdBaseHost);
    context.setResponse(new GslbResponse());
    return context;
  }

}
