package com.oneops.gslb;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;


import com.oneops.gslb.domain.Distribution;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.Gslb.Builder;

import com.oneops.gslb.domain.HealthCheck;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.Lb;

import com.oneops.gslb.domain.Protocol;
import com.oneops.gslb.domain.TorbitConfig;
import com.oneops.gslb.mtd.v2.domain.DcCloud;
import com.oneops.gslb.mtd.v2.domain.MtdBaseHostRequest;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MtdBaseTest {

  MtdHandler handler = new MtdHandler();

  @Before
  public void init() {
    loadCloudMap();
  }

  private void loadCloudMap() {
    handler.cloudMap.put("cl1", DcCloud.create(10, "cl1", 5, null));
    handler.cloudMap.put("cl2", DcCloud.create(12, "cl2", 5, null));
    handler.cloudMap.put("cl3", DcCloud.create(14, "cl3", 6, null));
  }

  @Test
  public void testMtdTargets() {
    Gslb gslb = request();
    ProvisionContext context = new ProvisionContext();
    initContext(gslb, context);
    try {
      List<MtdTarget> mtdTargets = handler.getMtdTargets(gslb, context);
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
  public void shouldAddHealthCheckForTcpPort() {
    List<HealthCheck> healthChecksDef = new ArrayList<>();
    healthChecksDef.add(HealthCheck.builder().protocol(Protocol.TCP).port(3306).path("/").build());
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(
        getRequestForHealthChecks(healthChecksDef));
    assertThat(healthChecks.size(), is(1));
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3306));
    assertThat(healthCheck.testObjectPath(), anyOf(nullValue(), is("")));
  }

  @Test
  public void allHttpPortsHaveHealthChecks() {
    List<HealthCheck> healthChecksDef = new ArrayList<>();
    healthChecksDef.add(HealthCheck.builder().protocol(Protocol.HTTP).port(80).path("/").build());
    healthChecksDef.add(HealthCheck.builder().protocol(Protocol.HTTP).port(90).path("/").build());
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(
        getRequestForHealthChecks(healthChecksDef));
    assertThat(healthChecks.size(), is(2));
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("http"));
    assertThat(healthCheck.port(), is(80));
    assertThat(healthCheck.testObjectPath(), is("/"));
    healthCheck = healthChecks.get(1);
    assertThat(healthCheck.protocol(), is("http"));
    assertThat(healthCheck.port(), is(90));
    assertThat(healthCheck.testObjectPath(), is("/"));
  }

  @Test
  public void httpAndTcpPortChecks() {
    List<HealthCheck> healthChecksDef = new ArrayList<>();
    healthChecksDef.add(HealthCheck.builder().protocol(Protocol.HTTP).port(80).path("/").build());
    healthChecksDef.add(HealthCheck.builder().protocol(Protocol.TCP).port(3306).path("/").build());
    List<MtdHostHealthCheck> healthChecks = handler.getHealthChecks(
        getRequestForHealthChecks(healthChecksDef));
    assertThat(healthChecks.size(), is(2));
    MtdHostHealthCheck healthCheck = healthChecks.get(0);
    assertThat(healthCheck.protocol(), is("http"));
    assertThat(healthCheck.port(), is(80));
    assertThat(healthCheck.testObjectPath(), is("/"));
    healthCheck = healthChecks.get(1);
    assertThat(healthCheck.protocol(), is("tcp"));
    assertThat(healthCheck.port(), is(3306));
  }

  @Test
  public void shouldChangeHostNameToLowerCase() throws Exception {
    Builder builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT1")
        .subdomain("STG.combo1.org1");
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    addDeployedLbs(builder);
    Gslb gslb = builder.build();
    ProvisionContext context = new ProvisionContext();
    initContext(gslb, context);
    MtdBaseHostRequest mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    assertThat(mtdBaseHostRequest.mtdHost().mtdHostName(), is("plt1"));
  }

  @Test
  public void testDistribution() throws Exception {
    Builder builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT2")
        .subdomain("STG.combo1.org2");
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    addDeployedLbs(builder);
    Gslb gslb = builder.build();
    ProvisionContext context = new ProvisionContext();
    initContext(gslb, context);
    MtdBaseHostRequest mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    assertThat(mtdBaseHostRequest.mtdHost().localityScope(), is(0));

    builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT2")
        .subdomain("STG.combo1.org2");
    builder.distribution(Distribution.ROUND_ROBIN);
    addHealthCheck(builder);
    addConfigs(builder);
    addDeployedLbs(builder);
    gslb = builder.build();
    context = new ProvisionContext();
    initContext(gslb, context);
    mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    assertThat(mtdBaseHostRequest.mtdHost().localityScope(), is(2));
  }

  @Test
  public void testLbWeights() throws Exception {
    Builder builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT3")
        .subdomain("STG.combo1.org3");
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    List<Lb> lbList = new ArrayList<>();
    lbList.add(Lb.create("cl1", "1.1.1.0", true, 20));
    lbList.add(Lb.create("cl2", "1.1.1.1", true, 30));
    lbList.add(Lb.create("cl3", "1.1.1.2", true, 50));
    builder.lbs(lbList);
    Gslb gslb = builder.build();
    ProvisionContext context = new ProvisionContext();
    initContext(gslb, context);
    MtdBaseHostRequest mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    List<MtdTarget> targets = mtdBaseHostRequest.mtdHost().mtdTargets();
    assertThat(targets.size(), is(3));
    assertThat(targets.get(0).weightPercent(), is(20));
    assertThat(targets.get(1).weightPercent(), is(30));
    assertThat(targets.get(2).weightPercent(), is(50));

    builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT3")
        .subdomain("STG.combo1.org3");
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    List<Lb> lbList1 = new ArrayList<>();
    lbList1.add(Lb.create("cl1", "1.1.1.0", true, null));
    lbList1.add(Lb.create("cl2", "1.1.1.1", true, null));
    builder.lbs(lbList1);
    gslb = builder.build();
    context = new ProvisionContext();
    initContext(gslb, context);
    mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    targets = mtdBaseHostRequest.mtdHost().mtdTargets();
    assertThat(targets.size(), is(2));
    assertTrue(targets.get(0).weightPercent() > 0 &&
        (targets.get(0).weightPercent() == targets.get(1).weightPercent()));
  }

  @Test
  public void testDisabledForTraffic() throws Exception {
    Builder builder = Gslb.builder();
    builder.logContextId("")
        .app("PLT4")
        .subdomain("STG.combo1.org4");
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    List<Lb> lbList = new ArrayList<>();
    lbList.add(Lb.create("cl1", "1.1.1.0", true, null));
    lbList.add(Lb.create("cl2", "1.1.1.1", false, null));
    builder.lbs(lbList);
    Gslb gslb = builder.build();
    ProvisionContext context = new ProvisionContext();
    initContext(gslb, context);
    MtdBaseHostRequest mtdBaseHostRequest = handler.mtdBaseHostRequest(gslb, context);
    List<MtdTarget> targets = mtdBaseHostRequest.mtdHost().mtdTargets();
    assertThat(targets.size(), is(2));
    assertThat(targets.get(0).weightPercent(), is(100));
    assertThat(targets.get(0).enabled(), is(true));
    assertThat(targets.get(1).weightPercent(), is(0));
    assertThat(targets.get(1).enabled(), is(false));
  }


  private Gslb getRequestForHealthChecks(List<HealthCheck> healthChecks) {
    Builder builder = Gslb.builder();
    addBase(builder);
    addDistribution(builder);
    addConfigs(builder);
    addDeployedLbs(builder);
    builder.healthChecks(healthChecks).logContextId("");
    Gslb request = builder.build();
    return request;
  }

  private void initContext(Gslb gslb, Context context) {
    context.logKey(gslb.logContextId());
    context.setTorbitClient(mock(TorbitClient.class));
    context.setTorbitApi(mock(TorbitApi.class));
  }

  private Gslb request() {
    Builder builder = Gslb.builder();
    builder.logContextId("");
    addBase(builder);
    addDistribution(builder);
    addHealthCheck(builder);
    addConfigs(builder);
    addDeployedLbs(builder);
    return builder.build();
  }

  private void addBase(Builder builder) {
    builder.app("plt1").subdomain("stg.combo1.org1");
  }

  private void addDistribution(Builder builder) {
    builder.distribution(Distribution.PROXIMITY);
  }

  private void addHealthCheck(Builder builder) {
    HealthCheck healthCheck = HealthCheck.builder()
        .protocol(Protocol.HTTP)
        .port(80)
        .path("/")
        .build();
    builder.healthChecks(Collections.singletonList(healthCheck));
  }

  private void addDeployedLbs(Builder builder) {
    List<Lb> lbList = new ArrayList<>();
    lbList.add(Lb.create("cl1", "1.1.1.0", true, 100));
    lbList.add(Lb.create("cl2", "1.1.1.1", true, 100));
    builder.lbs(lbList);
  }

  private void addConfigs(Builder builder) {
    TorbitConfig torbitConfig = TorbitConfig.create("https://localhost:8443", "test-oo",
        "test_auth", 101, "glb.xyz.com");
    InfobloxConfig infobloxConfig = InfobloxConfig.create("https://localhost:8121",
        "test-oo", "test_pwd", "prod.xyz.com");
    builder.torbitConfig(torbitConfig);
    builder.infobloxConfig(infobloxConfig);
  }

}
