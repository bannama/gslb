package com.oneops.gslb;


import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.oneops.gslb.v2.domain.MtdBaseResponse;
import com.oneops.gslb.v2.domain.MtdHost;
import com.oneops.gslb.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.v2.domain.MtdHostResponse;
import com.oneops.gslb.v2.domain.MtdTarget;
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
    List<String> clouds = getClouds();
    assumeTrue(config != null && clouds.size() >= 2);
    mtdHandler = new MtdHandler();
    mtdHandler.timeOut = "2s";
    mtdHandler.retryDelay = "30s";
    mtdHandler.interval = "5s";
    mtdHandler.failureCountToMarkDown = 3;
    mtdHandler.gson = new Gson();
    mtdHandler.jsonParser = new JsonParser();
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

    GslbRequest request = GslbRequest.builder().action(Action.ADD).
        assembly("a1").
        environment("e1").
        platform("p1").
        org("testo1").
        platformEnabled(true).
        customSubdomain("e1.a1.testo1").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(deployedLbs()).platformClouds(twoPrimaryClouds()).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    Context context = new Context(request);
    GslbResponse response = execute(context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
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

  private GslbResponse execute(Context context) {
    GslbResponse response = new GslbResponse();
    context.setResponse(response);
    mtdHandler.setupTorbitGdns(context);
    return response;
  }


  @Test
  public void addWithOnePrimarySecondaryForHttpProximity() throws Exception {
    List<Cloud> clouds = new ArrayList<>();
    clouds.add(Cloud.create(101, cloud1, "1", "active", null, null));
    clouds.add(Cloud.create(102, cloud2, "2", "active", null, null));
    GslbRequest request = GslbRequest.builder().action(Action.ADD).
        assembly("a2").
        environment("e2").
        platform("p2").
        org("testo2").
        platformEnabled(true).
        customSubdomain("e2.a2.testo2").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(deployedLbs()).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    Context context = new Context(request);
    GslbResponse response = execute(context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
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
  public void updateActionWithNewPrimaryCloud() throws Exception {
    List<Cloud> clouds = new ArrayList<>();
    clouds.add(Cloud.create(101, cloud1, "1", "active", null, null));

    List<DeployedLb> lbs = new ArrayList<>();
    lbs.add(DeployedLb.create("lb-101-1", "10.1.100.1"));

    GslbRequest request = GslbRequest.builder().action(Action.ADD).
        assembly("a3").
        environment("e3").
        platform("p3").
        org("testo3").
        platformEnabled(true).
        customSubdomain("e3.a3.testo3").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    Context context = new Context(request);
    GslbResponse response = execute(context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
    assertTrue(resp.isSuccessful());
    MtdHost mtdHost = resp.getBody().mtdHost();
    List<MtdTarget> mtdTargets = mtdHost.mtdTargets();
    assertThat(mtdTargets.size(), is(1));
    MtdTarget target1 = mtdTargets.get(0);
    assertThat(target1.mtdTargetHost(), is("10.1.100.1"));

    //add another cloud with udpate action
    clouds.add(Cloud.create(102, cloud2, "1", "active", null, null));
    lbs.add(DeployedLb.create("lb-102-1", "10.2.200.2"));
    request = GslbRequest.builder().action(Action.UPDATE).
        assembly("a3").
        environment("e3").
        platform("p3").
        org("testo3").
        platformEnabled(true).
        customSubdomain("e3.a3.testo3").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    context = new Context(request);
    response = execute(context);


    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
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
    List<Cloud> clouds = new ArrayList<>();
    clouds.add(Cloud.create(101, cloud1, "1", "active", null, null));
    clouds.add(Cloud.create(102, cloud2, "2", "active", null, null));

    List<DeployedLb> lbs = new ArrayList<>();
    lbs.add(DeployedLb.create("lb-101-1", "10.1.100.1"));
    lbs.add(DeployedLb.create("lb-102-1", "10.2.200.2"));

    GslbRequest request = GslbRequest.builder().action(Action.ADD).
        assembly("a4").
        environment("e4").
        platform("p4").
        org("testo4").
        platformEnabled(true).
        customSubdomain("e4.a4.testo4").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    Context context = new Context(request);
    GslbResponse response = execute(context);

    //add another cloud with primary secondary failover
    clouds.clear();
    clouds.add(Cloud.create(101, cloud1, "2", "active", null, null));
    clouds.add(Cloud.create(102, cloud2, "1", "active", null, null));

    request = GslbRequest.builder().action(Action.UPDATE).
        assembly("a4").
        environment("e4").
        platform("p4").
        org("testo4").
        platformEnabled(true).
        customSubdomain("e4.a4.testo4").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "2", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    context = new Context(request);
    response = execute(context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));
    assertNotNull(response.getMtdBaseId());
    assertNotNull(response.getMtdDeploymentId());

    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());
    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
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
    List<Cloud> clouds = new ArrayList<>();
    clouds.add(Cloud.create(101, cloud1, "1", "active", null, null));
    clouds.add(Cloud.create(102, cloud2, "2", "active", null, null));

    List<DeployedLb> lbs = new ArrayList<>();
    lbs.add(DeployedLb.create("lb-101-1", "10.1.100.1"));
    lbs.add(DeployedLb.create("lb-102-1", "10.2.200.2"));

    GslbRequest request = GslbRequest.builder().action(Action.ADD).
        assembly("a5").
        environment("e5").
        platform("p5").
        org("testo5").
        platformEnabled(true).
        customSubdomain("e5.a5.testo5").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    Context context = new Context(request);
    GslbResponse response = execute(context);
    int mtdBaseId = Integer.parseInt(response.getMtdBaseId());

    request = GslbRequest.builder().action(Action.DELETE).
        assembly("a5").
        environment("e5").
        platform("p5").
        org("testo5").
        platformEnabled(false).
        customSubdomain("e5.a5.testo5").
        lbConfig(httpLbConfig()).
        cloud(Cloud.create(101, cloud1, "1", "active", config, null)).
        deployedLbs(lbs).platformClouds(clouds).
        fqdn(Fqdn.create(null, null, "proximity")).
        build();

    context = new Context(request);
    response = execute(context);

    assertThat(response.getStatus(), anyOf(nullValue(), is(Status.SUCCESS)));

    Resp<MtdHostResponse> resp = context.getTorbitClient().execute(context.getTorbitApi().getMTDHost(
        mtdBaseId, request.platform()), MtdHostResponse.class);
    assertThat(resp.getCode(), is(404));

    context.getTorbitClient().execute(context.getTorbitApi().deleteMTDBase(mtdBaseId),
        MtdBaseResponse.class);
  }

  private List<DeployedLb> deployedLbs() {
    List<DeployedLb> lbs = new ArrayList<>();
    lbs.add(DeployedLb.create("lb-101-1", "10.1.100.1"));
    lbs.add(DeployedLb.create("lb-102-1", "10.2.200.2"));
    return lbs;
  }

  private List<Cloud> twoPrimaryClouds() {
    List<Cloud> clouds = new ArrayList<>();
    clouds.add(Cloud.create(101, cloud1, "1", "active", null, null));
    clouds.add(Cloud.create(102, cloud2, "1", "active", null, null));
    return clouds;
  }

  private LbConfig httpLbConfig() {
    return LbConfig.create("['http 80 http 8080']",  "{'8080':'GET /'}");
  }

}
