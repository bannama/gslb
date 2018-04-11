package com.oneops.gslb;

import com.oneops.gslb.domain.CloudARecord;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.HealthCheck;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.Lb;
import com.oneops.gslb.domain.ProvisionedGslb;
import com.oneops.gslb.domain.TorbitConfig;
import com.oneops.gslb.mtd.v2.domain.MtdBase;
import com.oneops.gslb.mtd.v2.domain.MtdBaseResponse;
import com.oneops.gslb.mtd.v2.domain.MtdHost;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdHostResponse;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import com.oneops.infoblox.InfobloxClient;
import com.oneops.infoblox.model.a.ARec;
import com.oneops.infoblox.model.cname.CNAME;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class GslbVerifier {

  private static final Logger logger = Logger.getLogger(GslbVerifier.class);

  public GslbProvisionResponse verifyCreate(Gslb gslb, GslbProvisionResponse response) {
    String logKey = gslb.logContextId();
    try {
      VerifyContext context = getContext(gslb.torbitConfig(), gslb.app(), gslb.subdomain());
      verifyMtdHost(gslb, response, context, true);
      verifyCnamesWithResultEntries(gslb, response, context);
    } catch (Exception e) {
      String msg = failureMessage(logKey,"wo failed during verify", e);
      return GslbProvisionResponse.failedResponse(msg);
    }
    return response;
  }

  public GslbResponse verifyDelete(ProvisionedGslb provisionedGslb, GslbResponse response) {
    String logKey = provisionedGslb.logContextId();
    try {
      VerifyContext context = getContext(provisionedGslb.torbitConfig(), provisionedGslb.app(),
          provisionedGslb.subdomain());
      logger.info(provisionedGslb.logContextId() + "platform is disabled, verify mtd delete and cname deletes");
      verifyMtdDelete(provisionedGslb, context);
      verifyInfobloxDelete(provisionedGslb, context);
    } catch (Exception e) {
      String msg = failureMessage(logKey, "wo failed during verify", e);
      return GslbResponse.failedResponse(msg);
    }
    return response;
  }

  private String failureMessage(String logKey, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(logKey + failMsg, e);
    return failMsg;
  }

  private void verifyCnamesWithResultEntries(Gslb gslb, GslbProvisionResponse response, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(gslb.infobloxConfig());
    String cname = (context.app + context.mtdBaseHost).toLowerCase();
    Map<String, String> dnsEntries = response.getDnsEntries();
    verify(() -> dnsEntries != null && !dnsEntries.isEmpty(), "response has dns entries");
    logger.info(gslb.logContextId() + "response entries map " + response.getDnsEntries());
    for (String al : gslb.cnames()) {
      String alias = al.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames != null && cnames.size() == 1 && cnames.get(0).canonical().equals(cname),
          "cname verify failed " + alias);
      verify(() -> cname.equals(dnsEntries.get(alias)), "result ci entries attribute has entry for alias " + alias);
    }
    verify(() -> dnsEntries.get(cname) != null, "result ci entries attribute value for " + cname + " is present ");

    if (gslb.cloudARecords() != null && !gslb.cloudARecords().isEmpty()) {
      Map<String, Lb> cloudLbMap = gslb.lbs().stream().collect(Collectors.toMap(l -> l.cloud(), l->l));
      for (CloudARecord aRecord : gslb.cloudARecords()) {
        List<ARec> records = infobloxClient.getARec(aRecord.aRecord());
        if (cloudLbMap.containsKey(aRecord.cloud())) {
          String lbVip = cloudLbMap.get(aRecord.cloud()).vip();
          logger.info(gslb.logContextId() + "cloud entry records " + records.size());
          if (StringUtils.isNotBlank(lbVip)) {
            verify(() -> records != null && records.size() == 1 && records.get(0).ipv4Addr().equals(lbVip),
                "cloud cname verify failed " + aRecord.aRecord());
            verify(() -> lbVip.equals(dnsEntries.get(aRecord.cloud())),
                "result ci entries attribute has entry for cloud cname " + aRecord.cloud());
          }
        }
      }
    }
  }

  private void verifyCnames(Gslb gslb, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(gslb.infobloxConfig());
    String cname = (context.app + context.mtdBaseHost).toLowerCase();
    for (String alias : gslb.cnames()) {
      alias = alias.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames != null && cnames.size() == 1 && cnames.get(0).canonical().equals(cname),
          "cname verify failed " + alias);
    }
  }

  private void verifyInfobloxDelete(ProvisionedGslb provisionedGslb, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(provisionedGslb.infobloxConfig());
    for (String alias : provisionedGslb.cnames()) {
      alias = alias.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames == null || cnames.isEmpty(), "cname delete verify failed " + alias);
    };
    verifyCloudCnameDelete(provisionedGslb, context, infobloxClient);
  }

  private void verifyCloudCnameDelete(ProvisionedGslb provisionedGslb, VerifyContext context,
      InfobloxClient infobloxClient) throws Exception {
    if (provisionedGslb.cloudARecords() != null) {
      for (CloudARecord cloudARecord : provisionedGslb.cloudARecords()) {
        List<CNAME> cnames = infobloxClient.getCNameRec(cloudARecord.aRecord());
        verify(() -> cnames == null || cnames.isEmpty(), "cloud cname delete verify failed " + cloudARecord.aRecord());
      }
    }
  }

  private InfobloxClient getInfoBloxClient(InfobloxConfig infobloxConfig) {
    return InfobloxClient.builder().endPoint(infobloxConfig.host()).
        userName(infobloxConfig.user()).
        password(infobloxConfig.pwd()).
        tlsVerify(false).
        build();
  }

  private void verifyMtdDelete(ProvisionedGslb provisionedGslb, VerifyContext context) throws Exception {
    TorbitClient client = context.torbitClient;
    TorbitApi torbit = context.torbit;
    Resp<MtdBaseResponse> resp = client.execute(torbit.getMTDBase(context.mtdBaseHost), MtdBaseResponse.class);
    logger.info(provisionedGslb.logContextId() + "verifying mtd host not exists ");
    if (resp.isSuccessful()) {
      logger.info(provisionedGslb.logContextId() + "mtd base exists, trying to get mtd host");
      MtdBase mtdBase = resp.getBody().mtdBase();
      Resp<MtdHostResponse> hostResp = client.execute(torbit.getMTDHost(mtdBase.mtdBaseId(), context.app), MtdHostResponse.class);
      logger.info("hostResp response " + hostResp.getBody());
      verify(() -> !hostResp.isSuccessful(), "mtd host is not available");
    }
  }

  private void verifyMtdHost(Gslb gslb, GslbProvisionResponse response, VerifyContext context,
      boolean verifyResultEntries) throws Exception {
    TorbitClient client = context.torbitClient;
    TorbitApi torbit = context.torbit;

    logger.info(gslb.logContextId() + "verifying for platform " + context.app);
    Resp<MtdBaseResponse> resp = client.execute(torbit.getMTDBase(context.mtdBaseHost), MtdBaseResponse.class);
    logger.info(gslb.logContextId() + "verifying mtd base ");
    verify(() -> resp.isSuccessful(), "mtd base exists");
    MtdBase mtdBase = resp.getBody().mtdBase();
    verify(() -> context.mtdBaseHost.equals(mtdBase.mtdBaseName()), "mtd base name match", context.mtdBaseHost, mtdBase.mtdBaseName());

    Resp<MtdHostResponse> hostResp = client.execute(torbit.getMTDHost(mtdBase.mtdBaseId(), context.app), MtdHostResponse.class);
    logger.info(gslb.logContextId() + "verifying mtd host version exists");
    verify(() -> hostResp.isSuccessful(), "mtd host version exists");

    MtdHost host = hostResp.getBody().mtdHost();
    logger.info(gslb.logContextId() + "verifying mtd host targets");
    List<MtdTarget> targets = host.mtdTargets();
    logger.info(gslb.logContextId() + "configured mtd targets " + targets.stream().
        map(MtdTarget::mtdTargetHost).
        collect(Collectors.joining(",")));
    Map<String, MtdTarget> map = targets.stream().collect(Collectors.toMap(MtdTarget::mtdTargetHost, Function
        .identity()));
    List<Lb> lbList = gslb.lbs();
    logger.info(gslb.logContextId() + "expected targets " +
        lbList.stream().map(l -> l.vip()).collect(Collectors.joining(",")));
    for (Lb lb : lbList) {
      verify(() -> map.containsKey(lb.vip()), "lb vip present in MTD target");
      MtdTarget target = map.get(lb.vip());
      verify(() -> lb.enabledForTraffic() ? target.enabled() : !target.enabled(),
          "mtd target enabled/disabled based on cloud status");
    }
    context.primaryTargets = lbList.stream().filter(lb -> lb.enabledForTraffic()).map(lb -> lb.vip()).collect(Collectors.toList());

    logger.info(gslb.logContextId() + "verifying mtd health checks");
    List<MtdHostHealthCheck> healthChecks = host.mtdHealthChecks();

    logger.info(gslb.logContextId() + "gslb.healthChecks() : " + gslb.healthChecks());
    logger.info(gslb.logContextId() + "actual health checks : " + healthChecks);

    verify(() -> ((healthChecks != null ? healthChecks.size() : 0) ==
        (gslb.healthChecks() != null ? gslb.healthChecks().size() : 0)), "all health checks are configured");
    if (gslb.healthChecks() != null && !gslb.healthChecks().isEmpty()) {
      Map<Integer, HealthCheck> expectedChecksMap = gslb.healthChecks().stream().collect(Collectors.toMap(h -> h.port(), h->h));

      for (MtdHostHealthCheck healthCheck : healthChecks) {
        verify(() -> expectedChecksMap.containsKey(healthCheck.port()), "mtd health check available for port");
        HealthCheck healthCheckDefn = expectedChecksMap.get(healthCheck.port());
        verify(() -> healthCheckDefn.protocol().toString().toLowerCase().equals(healthCheck.protocol()), "mtd health protocol matches");
        if (StringUtils.isNotBlank(healthCheckDefn.path())) {
          verify(() -> healthCheckDefn.path().equals(healthCheck.testObjectPath()), "mtd health ecv matches");
        }
      }
    }
    if (verifyResultEntries)
      verify(() -> StringUtils.isNotBlank(response.getMtdBaseId()) &&
              StringUtils.isNotBlank(response.getGlb()) &&
              StringUtils.isNotBlank(response.getMtdDeploymentId()),
          "result ci contains gslb_map attribute");
  }

  private void verify(Condition condition, String message) throws Exception {
    if (!condition.test()) {
      throw new Exception("Verification failed for : " + message);
    }
  }

  private void verify(Condition condition, String message, String expected, String actual) throws Exception {
    if (!condition.test()) {
      throw new Exception("Verification failed for : " + message + ":: Expected: " + expected + ", actual: " + actual);
    }
  }

  private VerifyContext getContext(TorbitConfig torbitConfig, String app, String subdomain) throws Exception {
    VerifyContext context = new VerifyContext();
    context.torbitClient = new TorbitClient(torbitConfig);
    context.torbit = context.torbitClient.getTorbit();
    context.mtdBaseHost = ("." + subdomain + "." + torbitConfig.gslbBaseDomain()).toLowerCase();
    context.app = app.toLowerCase();
    return context;
  }

  class VerifyContext {
    TorbitClient torbitClient;
    TorbitApi torbit;
    String mtdBaseHost;
    String app;
    List<String> primaryTargets;
  }

}
