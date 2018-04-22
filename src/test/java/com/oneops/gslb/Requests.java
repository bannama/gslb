package com.oneops.gslb;

import com.oneops.gslb.domain.CloudARecord;
import com.oneops.gslb.domain.Distribution;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.Gslb.Builder;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.HealthCheck;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.Lb;
import com.oneops.gslb.domain.Protocol;
import com.oneops.gslb.domain.ProvisionedGslb;
import com.oneops.gslb.domain.TorbitConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Requests {

  public static Gslb getProvisingRequest(String app, String subDomian, String primaryVip,
      String cloud, String zone, InfobloxConfig infobloxConfig,
      List<String> cnames, List<CloudARecord> aRecords, List<String> obsoleteCnames, List<CloudARecord> obsoleteARecords) {
    Builder builder = Gslb.builder();
    builder.app(app).subdomain(subDomian);
    builder.distribution(Distribution.PROXIMITY);
    builder.healthChecks(Collections.singletonList(HealthCheck.builder().protocol(Protocol.HTTP).port(80).path("/").build()));
    builder.logContextId("");

    List<Lb> deployedLbs = new ArrayList<>();
    deployedLbs.add(Lb.create(cloud, primaryVip,true, 50));
    deployedLbs.add(Lb.create("dummyCloud", "1.1.1.1",true, 50));
    builder.lbs(deployedLbs);
    builder.cnames(cnames);
    builder.cloudARecords(aRecords);
    builder.obsoleteCnames(obsoleteCnames);
    builder.obsoleteCloudARecords(obsoleteARecords);

    TorbitConfig torbitConfig = TorbitConfig.create("https://localhost:8443", "test-oo",
        "test_auth", 101, "glb.xyz.com");
    if (infobloxConfig == null)
      infobloxConfig = InfobloxConfig.create("https://localhost:8121",
        "test-oo", "test_pwd", zone);
    builder.torbitConfig(torbitConfig);
    builder.infobloxConfig(infobloxConfig);
    return builder.build();
  }

  public static ProvisionedGslb getProvisionedGslb(String app, String subDomian,
      String zone, InfobloxConfig infobloxConfig,
      List<String> cnames, List<CloudARecord> aRecords) {
    ProvisionedGslb.Builder builder = ProvisionedGslb.builder();
    builder.app(app).subdomain(subDomian);
    builder.logContextId("");

    builder.cnames(cnames);
    builder.cloudARecords(aRecords);

    TorbitConfig torbitConfig = TorbitConfig.create("https://localhost:8443", "test-oo",
        "test_auth", 101, "glb.xyz.com");
    if (infobloxConfig == null)
      infobloxConfig = InfobloxConfig.create("https://localhost:8121",
          "test-oo", "test_pwd", zone);
    builder.torbitConfig(torbitConfig);
    builder.infobloxConfig(infobloxConfig);
    return builder.build();
  }

  public static ProvisionContext getProvisionContext(String app, String mtdBaseName) {
    ProvisionContext provisionContext = new ProvisionContext();
    provisionContext.setMtdBaseName(mtdBaseName);
    provisionContext.setApp(app);
    provisionContext.logKey("");
    provisionContext.setProvisioningResponse(new GslbProvisionResponse());
    return provisionContext;
  }

  public static Context getContext(String app, String mtdBaseName) {
    Context context = new Context();
    context.setMtdBaseName(mtdBaseName);
    context.setApp(app);
    context.logKey("");
    context.setResponse(new GslbResponse());
    return context;
  }

}
