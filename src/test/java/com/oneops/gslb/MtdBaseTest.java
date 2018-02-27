package com.oneops.gslb;

import static org.mockito.Mockito.mock;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.oneops.gslb.GslbRequest.Builder;
import com.oneops.gslb.v2.domain.DcCloud;
import com.oneops.gslb.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.v2.domain.MtdTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MtdBaseTest {

  MtdHandler handler = new MtdHandler();

  @Before
  public void init() {
    loadCloudMap();
    handler.jsonParser = new JsonParser();
    Gson gson = new Gson();
    handler.gson = gson;
    handler.interval = "3s";
    handler.timeOut = "2s";
    handler.failureCountToMarkDown = 3;
    handler.retryDelay = "20s";

  }

  private void loadCloudMap() {
    handler.cloudMap.put("cl1", DcCloud.create(10, "cl1", 5, null));
    handler.cloudMap.put("cl2", DcCloud.create(12, "cl2", 5, null));
  }


  @Test
  public void testMtdTargets() {
    Context context = getContext();
    try {
      List<MtdTarget> mtdTargets = handler.getMtdTargets(context);
      Assert.assertEquals(2, mtdTargets.size());
      MtdTarget target1 = mtdTargets.get(0);
      Assert.assertEquals(Long.valueOf(10), new Long(target1.cloudId()));
      Assert.assertEquals(true, target1.enabled());
      Assert.assertEquals("1.1.1.0",target1.mtdTargetHost());

      MtdTarget target2 = mtdTargets.get(1);
      Assert.assertEquals(Long.valueOf(12), new Long(target2.cloudId()));
      Assert.assertEquals(true, target2.enabled());
      Assert.assertEquals("1.1.1.1",target2.mtdTargetHost());

    } catch(Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testMtdHealthCheck() {
    Context context = getContext();
    try {
      List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(context);
      Assert.assertEquals(1, healthChecks.size());
      MtdHostHealthCheck healthCheck = healthChecks.get(0);
      Assert.assertEquals(80, healthCheck.port().longValue());
      Assert.assertEquals("http", healthCheck.protocol());
      Assert.assertEquals("/", healthCheck.testObjectPath());
    } catch(Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  private Context getContext() {
    Context context = new Context(request());
    context.setTorbitClient(mock(TorbitClient.class));
    return context;
  }

  private GslbRequest request() {
    Builder builder = GslbRequest.builder();
    addBase(builder);
    addFqdn(builder);
    addLbConfig(builder);
    addClouds(builder);
    addDeployedLbs(builder);
    return builder.build();
  }

  private void addBase(Builder builder) {
    builder.platform("plt1").assembly("combo1").environment("stg").org("org1").action(Action.ADD).platformEnabled(true);
  }

  private void addFqdn(Builder builder) {
    builder.fqdn(Fqdn.create(null, null, "proximity"));
  }

  private void addLbConfig(Builder builder) {
    builder.lbConfig(LbConfig.create("['http 80 http 80']",  "{'80':'GET /'}"));
  }

  private void addDeployedLbs(Builder builder) {
    List<DeployedLb> deployedLbs = new ArrayList<>();
    deployedLbs.add(DeployedLb.create("lb-101-1", "1.1.1.0"));
    deployedLbs.add(DeployedLb.create("lb-102-1", "1.1.1.1"));
    builder.deployedLbs(deployedLbs);
  }

  private void addClouds(Builder builder) {
    TorbitConfig torbitConfig = TorbitConfig.create("https://localhost:8443", "test-oo",
        "test_auth", 101, "glb.xyz.com");
    InfobloxConfig infobloxConfig = InfobloxConfig.create("https://localhost:8121",
        "test-oo", "test_pwd", "prod.xyz.com");
    List<Cloud> clouds = new ArrayList<>();

    clouds.add(Cloud.create(101, "cl1", "1", "active", null, null));
    clouds.add(Cloud.create(102, "cl2", "1", "active", null, null));
    builder.platformClouds(clouds);
    builder.cloud(Cloud.create(101, "cl1", "1", "active", torbitConfig, infobloxConfig));
  }

}
