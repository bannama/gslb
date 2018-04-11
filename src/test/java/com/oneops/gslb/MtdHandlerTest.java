package com.oneops.gslb;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.oneops.gslb.domain.Distribution;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.HealthCheck;
import com.oneops.gslb.domain.Lb;
import com.oneops.gslb.domain.Protocol;
import com.oneops.gslb.domain.ProvisionedGslb;
import com.oneops.gslb.domain.TorbitConfig;
import com.oneops.gslb.mtd.v2.domain.MtdBaseResponse;
import com.oneops.gslb.mtd.v2.domain.MtdHost;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdHostResponse;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MtdHandlerTest {

  TorbitConfig config;
  MtdHandler mtdHandler;
  String cloud1;
  String cloud2;

  @Before
  public void setup() {
    config = getTorbitConfig();
    mtdHandler = new MtdHandler();
    List<String> clouds = getClouds();
    assumeTrue(config != null && clouds.size() >= 2);
    cloud1 = clouds.get(0);
    cloud2 = clouds.get(1);
  }

  private TorbitConfig getTorbitConfig() {
    TorbitConfig config = null;
    try {
      config = TorbitConfig.create(getEnv("tb_endpoint"), getEnv("tb_user"),
          getEnv("tb_api_key"), Integer.parseInt(getEnv("tb_group_id")), getEnv("gslb_base_domain"));
    } catch (Exception e) {
    }
    return config;
  }

  private List<String> getClouds() {
    String clouds = getEnv("clouds");
    if (clouds != null) {
      return Arrays.asList(clouds.split(","));
    }
    return Collections.emptyList();
  }

  private String getEnv(String envName) {
    String val = System.getProperty(envName, System.getenv(envName));
    return val;
  }

  @Test
  public void addWithTwoPrimaryCloudsForHttpProximity() throws Exception {

    Gslb gslb = Gslb.builder()
        .app("p1")
        .subdomain("e1.a1.testo1")
        .lbs(lbsTwoPrimary())
        .healthChecks(Collections.singletonList(healthCheck()))
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    ProvisionContext context = new ProvisionContext();
    GslbProvisionResponse response = executeSetup(gslb, context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    MtdHost mtdHost = resp.getBody().mtdHost();
    List<MtdTarget> mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(2));
    MtdTarget target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.1.100.1"));
    assertTrue(target1.enabled());
    MtdTarget target2 = mtdTargets.get(1);
    assertThat(target2.mtdTargetHost(), is("10.2.200.2"));
    assertTrue(target2.enabled());

    List<MtdHostHealthCheck> healthChecks = mtdHost.mtdHealthChecks();
    assertTrue((healthChecks != null) && (healthChecks.size() == 1));
    MtdHostHealthCheck hc = healthChecks.get(0);
    assertThat(hc.protocol(), is("http"));
    assertThat(hc.port(), is(80));
    assertThat(hc.expectedStatus(), is(200));
    assertThat(hc.testObjectPath(), is("/"));

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }

  private GslbProvisionResponse executeSetup(Gslb gslb, ProvisionContext context) {
    GslbProvisionResponse response = new GslbProvisionResponse();
    context.setProvisioningResponse(response);
    context.logKey(gslb.logContextId());
    mtdHandler.setupTorbitGslb(gslb, context);
    return context.getProvisioningResponse();
  }

  private GslbProvisionResponse executeStatus(Gslb gslb, ProvisionContext context) {
    GslbProvisionResponse response = new GslbProvisionResponse();
    context.setProvisioningResponse(response);
    context.logKey(gslb.logContextId());
    mtdHandler.checkStatus(gslb, context);
    return context.getProvisioningResponse();
  }

  private List<Lb> lbsTwoPrimary() {
    List<Lb> lbs = new ArrayList<>();
    lbs.add(Lb.create(cloud1,"10.1.100.1", true));
    lbs.add(Lb.create(cloud2,"10.2.200.2", true));
    return lbs;
  }

  private List<Lb> lbsOnePrimaryOneSecondary() {
    List<Lb> lbs = new ArrayList<>();
    lbs.add(Lb.create(cloud1,"10.1.100.1", true));
    lbs.add(Lb.create(cloud2,"10.2.200.2", false));
    return lbs;
  }

  private HealthCheck healthCheck() {
    return HealthCheck.builder().protocol(Protocol.HTTP).port(80).path("/").build();
  }


  @Test
  public void addWithOnePrimarySecondaryForHttpProximity() throws Exception {
    Gslb gslb = Gslb.builder()
        .app("p2")
        .subdomain("e2.a2.testo2")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbsOnePrimaryOneSecondary())
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    ProvisionContext context = new ProvisionContext();
    GslbProvisionResponse response = executeSetup(gslb, context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    MtdHost mtdHost = resp.getBody().mtdHost();
    List<MtdTarget> mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(2));
    MtdTarget target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.1.100.1"));
    assertTrue(target1.enabled());
    MtdTarget target2 = mtdTargets.get(1);
    assertThat(target2.mtdTargetHost(), is("10.2.200.2"));
    assertFalse(target2.enabled());

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }


  @Test
  public void updateWithNewPrimaryCloud() throws Exception {
    List<Lb> lbs = new ArrayList<>();
    lbs.add(Lb.create(cloud1, "10.1.100.1", true));
    Gslb gslb = Gslb.builder()
        .app("p3")
        .subdomain("e3.a3.testo3")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    ProvisionContext context = new ProvisionContext();
    GslbProvisionResponse response = executeSetup(gslb, context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    MtdHost mtdHost = resp.getBody().mtdHost();
    List<MtdTarget> mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(1));
    MtdTarget target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.1.100.1"));

    //add another cloud
    lbs.add(Lb.create(cloud2, "10.2.200.2", true));
    gslb = Gslb.builder()
        .app("p3")
        .subdomain("e3.a3.testo3")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    context = new ProvisionContext();
    response = executeSetup(gslb, context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    mtdHost = resp.getBody().mtdHost();
    mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(2));
    target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.1.100.1"));
    assertTrue(target1.enabled());
    MtdTarget target2 = mtdTargets.get(1);
    assertThat(target2.mtdTargetHost(), is("10.2.200.2"));
    assertTrue(target2.enabled());

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }


  @Test
  public void updateActionWithCloudFailover() throws Exception {
    List<Lb> lbs = new ArrayList<>();
    lbs.add(Lb.create(cloud1, "10.1.100.1", true));
    lbs.add(Lb.create(cloud2, "10.2.200.2", false));
    Gslb gslb = Gslb.builder()
        .app("p4")
        .subdomain("e4.a4.testo4")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    ProvisionContext context = new ProvisionContext();
    GslbProvisionResponse response = executeSetup(gslb, context);

    //add another cloud with primary secondary failover
    lbs.clear();
    lbs.add(Lb.create(cloud2, "10.2.200.2", true));
    lbs.add(Lb.create(cloud1, "10.1.100.1", false));
    gslb = Gslb.builder()
        .app("p4")
        .subdomain("e4.a4.testo4")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    context = new ProvisionContext();
    response = executeSetup(gslb, context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    MtdHost mtdHost = resp.getBody().mtdHost();
    List<MtdTarget> mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(2));
    MtdTarget target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.2.200.2"));
    assertTrue(target1.enabled());
    MtdTarget target2 = mtdTargets.get(1);
    assertThat(target2.mtdTargetHost(), is("10.1.100.1"));
    assertFalse(target2.enabled());

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }


  @Test
  public void platformDisable() throws Exception {
    List<Lb> lbs = new ArrayList<>();
    lbs.add(Lb.create(cloud1, "10.1.100.1", true));
    lbs.add(Lb.create(cloud2, "10.2.200.2", true));
    Gslb gslb = Gslb.builder()
        .app("p5")
        .subdomain("e5.a5.testo5")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    ProvisionContext provisionContext = new ProvisionContext();
    GslbProvisionResponse provisionResponse = executeSetup(gslb, provisionContext);

    int mtdBaseId = Integer.parseInt(provisionResponse.getMtdBaseId());

    ProvisionedGslb provisionedGslb = ProvisionedGslb.builder()
        .app("p5")
        .subdomain("e5.a5.testo5")
        .torbitConfig(config)
        .build();
    Context context = new Context();
    context.setResponse(new GslbResponse());
    context.logKey(gslb.logContextId());
    mtdHandler.deleteGslb(provisionedGslb, context);
    GslbResponse response = context.getResponse();

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));

    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, gslb.app()), MtdHostResponse.class);
    assertThat(resp.getCode(), is(404));

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }


  @Test
  public void statusCheckAfterAdding() throws Exception {
    Gslb gslb = Gslb.builder()
        .app("P2")
        .subdomain("e2.a2.testo2")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbsTwoPrimary())
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    ProvisionContext provisionContext = new ProvisionContext();
    GslbProvisionResponse provisionResponse = executeSetup(gslb, provisionContext);
    int mtdBaseId = Integer.parseInt(provisionResponse.getMtdBaseId());

    gslb = Gslb.builder()
        .app("P2")
        .subdomain("e2.a2.testo2")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbsTwoPrimary())
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    provisionContext = new ProvisionContext();
    provisionResponse = executeStatus(gslb, provisionContext);
    assertThat(provisionResponse.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));

    provisionContext.getTorbitClient().execute(provisionContext.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }

  @Test
  public void failForStatusCheckWithDifferentHosts() throws Exception {
    List<Lb> lbs = lbsTwoPrimary();
    Gslb gslb = Gslb.builder()
        .app("p3")
        .subdomain("e3.a3.testo3")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();
    ProvisionContext provisionContext = new ProvisionContext();
    GslbProvisionResponse provisionResponse = executeSetup(gslb, provisionContext);
    int mtdBaseId = Integer.parseInt(provisionResponse.getMtdBaseId());

    lbs.remove(0);
    gslb = Gslb.builder()
        .app("p3")
        .subdomain("e3.a3.testo3")
        .healthChecks(Collections.singletonList(healthCheck()))
        .lbs(lbs)
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    provisionContext = new ProvisionContext();
    provisionResponse = executeStatus(gslb, provisionContext);
    assertThat(provisionResponse.getStatus(), is(Status.FAILED));

    provisionContext.getTorbitClient().execute(provisionContext.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }

  @Test
  public void testGslbProvider() {
    Gslb gslb = Gslb.builder()
        .app("p1")
        .subdomain("e1.a1.testo1")
        .lbs(lbsTwoPrimary())
        .healthChecks(Collections.singletonList(healthCheck()))
        .distribution(Distribution.PROXIMITY)
        .torbitConfig(config)
        .build();

    GslbProvider provider = new GslbProvider();
    GslbProvisionResponse response = provider.create(gslb);

    assertThat(response.getStatus(), is(Status.SUCCESS));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    ProvisionedGslb provisionedGslb = ProvisionedGslb.builder()
        .app(gslb.app())
        .subdomain(gslb.subdomain())
        .torbitConfig(gslb.torbitConfig())
        .build();
    GslbResponse response1 = provider.delete(provisionedGslb);
    assertThat(response1.getStatus(), is(Status.SUCCESS));
  }

}
