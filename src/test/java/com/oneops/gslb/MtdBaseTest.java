package com.oneops.gslb;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.oneops.gslb.domain.Action;
import com.oneops.gslb.domain.Cloud;
import com.oneops.gslb.domain.DeployedLb;
import com.oneops.gslb.domain.Fqdn;
import com.oneops.gslb.domain.GslbRequest;
import com.oneops.gslb.domain.GslbRequest.Builder;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.LbConfig;
import com.oneops.gslb.domain.TorbitConfig;
import com.oneops.gslb.mtd.v2.domain.DcCloud;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import java.util.ArrayList;
import java.util.List;
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
      assertEquals(2, mtdTargets.size());
      MtdTarget target1 = mtdTargets.get(0);
      assertEquals(Long.valueOf(10), new Long(target1.cloudId()));
      assertEquals(true, target1.enabled());
      assertEquals("1.1.1.0",target1.mtdTargetHost());

      MtdTarget target2 = mtdTargets.get(1);
      assertEquals(Long.valueOf(12), new Long(target2.cloudId()));
      assertEquals(true, target2.enabled());
      assertEquals("1.1.1.1",target2.mtdTargetHost());

    } catch(Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void allTcpPortsHaveHealthChecks() {
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(getContextForHealthChecks("['tcp 3306 tcp 3307', 'tcp 3308 tcp 3309']", "{'3309':'port-check'}"));
    assertThat(healthChecks.size(), is(2));

    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3306));
    assertThat(healthCheck.testObjectPath(), anyOf(nullValue(), is("")));

    healthCheck = healthChecks.get(1);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3308));
    assertThat(healthCheck.testObjectPath(), anyOf(nullValue(), is("")));
  }

  @Test
  public void shouldAddDefaultHealthCheckForTcpPort() {
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(getContextForHealthChecks("['tcp 3306 tcp 3307']", null));
    assertThat(healthChecks.size(), is(1));
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3306));
    assertThat(healthCheck.testObjectPath(), anyOf(nullValue(), is("")));

    healthChecks = handler.getHealthChecks(getContextForHealthChecks("['tcp 3306 tcp 3307']", "[]"));
    assertThat(healthChecks.size(), is(1));
    healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3306));
    assertThat(healthCheck.testObjectPath(), anyOf(nullValue(), is("")));
  }

  @Test
  public void allHttpPortsWithEcvHaveHealthChecks() {
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(getContextForHealthChecks("['http 80 http 8080', 'http 90 http 9090']", "{'8080':'GET /'}"));
    assertThat(healthChecks.size(), is(1));
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("http"));
    assertThat(healthCheck.port(), is(80));
    assertThat(healthCheck.testObjectPath(), is("/"));
  }

  @Test
  public void testMtdHealthCheckForHttp() {
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(getContextForHealthChecks("['http 80 http 8080']", "{'8080':'GET /'}"));
    assertEquals(1, healthChecks.size());
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertEquals(80, healthCheck.port().longValue());
    assertEquals("http", healthCheck.protocol());
    assertEquals("/", healthCheck.testObjectPath());
  }

  private Context getContextForHealthChecks(String listeners, String ecvMap) {
    Builder builder = GslbRequest.builder();
    addBase(builder);
    addFqdn(builder);
    addClouds(builder);
    addDeployedLbs(builder);
    builder.lbConfig(LbConfig.create(listeners, ecvMap));
    builder.logContextId("");
    GslbRequest request = builder.build();
    return new Context(request);
  }

  private Context getContext() {
    Context context = new Context(request());
    context.setTorbitClient(mock(TorbitClient.class));
    return context;
  }

  private GslbRequest request() {
    Builder builder = GslbRequest.builder();
    builder.logContextId("");
    addBase(builder);
    addFqdn(builder);
    addLbConfig(builder);
    addClouds(builder);
    addDeployedLbs(builder);
    return builder.build();
  }

  private void addBase(Builder builder) {
    builder.platform("plt1").assembly("combo1").environment("stg").org("org1").action(Action.add).platformEnabled(true);
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
