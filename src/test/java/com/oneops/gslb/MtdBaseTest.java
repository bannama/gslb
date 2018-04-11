package com.oneops.gslb;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
    lbList.add(Lb.create("cl1", "1.1.1.0", true));
    lbList.add(Lb.create("cl2", "1.1.1.1", true));
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
