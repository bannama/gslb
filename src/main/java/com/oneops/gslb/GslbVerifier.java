package com.oneops.gslb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oneops.gslb.domain.Action;
import com.oneops.gslb.domain.Cloud;
import com.oneops.gslb.domain.DeployedLb;
import com.oneops.gslb.domain.GslbRequest;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.LbConfig;
import com.oneops.gslb.mtd.v2.domain.MtdBase;
import com.oneops.gslb.mtd.v2.domain.MtdBaseResponse;
import com.oneops.gslb.mtd.v2.domain.MtdHost;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdHostResponse;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import com.oneops.infoblox.InfobloxClient;
import com.oneops.infoblox.model.a.ARec;
import com.oneops.infoblox.model.cname.CNAME;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GslbVerifier {

  private static final Logger logger = Logger.getLogger(GslbVerifier.class);

  @Autowired
  private JsonParser jsonParser;

  public GslbResponse verify(GslbRequest request, GslbResponse response) {
    String logKey = request.logContextId();
    try {
      VerifyContext context = getContext(request);
      context.logKey = logKey;

      if (request.action() == Action.delete) {
        if (request.platformEnabled()) {
          logger.info(context.logKey + "platform is enabled, verify only cname deletion");
          verifyMtdHost(request, response, context, false);
          verifyCloudCnameDelete(request, context, getInfoBloxClient(request));
          verifyCnames(request, context);
        }
        else {
          logger.info(context.logKey + "platform is disabled, verify mtd delete and cname deletes");
          verifyMtdDelete(context);
          verifyInfobloxDelete(request, context);
        }
      }
      else {
        verifyMtdHost(request, response, context, true);
        verifyCnamesWithResultEntries(request, response, context);
      }
    } catch (Exception e) {
      return failedResponse(logKey,"wo failed during verify", e);
    }
    return response;
  }

  private GslbResponse failedResponse(String logKey, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(logKey + failMsg, e);
    return GslbResponse.failedResponse(failMsg);
  }

  private void verifyCnamesWithResultEntries(GslbRequest request, GslbResponse response, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(request);
    String cname = (context.platform + context.mtdBaseHost).toLowerCase();
    Map<String, String> dnsEntries = response.getDnsEntries();
    verify(() -> dnsEntries != null && !dnsEntries.isEmpty(), "response has dns entries");
    logger.info(context.logKey + "response entries map " + response.getDnsEntries());
    for (String al : getAliases(request, context)) {
      String alias = al.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames != null && cnames.size() == 1 && cnames.get(0).canonical().equals(cname),
          "cname verify failed " + alias);
      verify(() -> cname.equals(dnsEntries.get(alias)), "result ci entries attribute has entry for alias " + alias);
    }
    verify(() -> dnsEntries.get(cname) != null, "result ci entries attribute value for " + cname + " is present ");

    String cloudCname = getCloudCname(request, context).toLowerCase();

    Optional<DeployedLb> opt = request.deployedLbs().stream().filter(lb -> {
      String[] elements = lb.name().split("-");
      return request.cloud().id() == (Long.parseLong(elements[elements.length-2]));
    }).findFirst();

    verify(() -> opt.isPresent(), "DeployedLb present in request for this cloud");

    List<ARec> records = infobloxClient.getARec(cloudCname);
    String lbVip = opt.get().dnsRecord();
    logger.info(context.logKey + "cloud entry records " + records.size());
    if (StringUtils.isNotBlank(lbVip)) {
      verify(() -> records != null && records.size() == 1 && records.get(0).ipv4Addr().equals(lbVip),
          "cloud cname verify failed " + cloudCname);
      verify(() -> lbVip.equals(dnsEntries.get(cloudCname)),
          "result ci entries attribute has entry for cloud cname " + cloudCname);
    }
  }

  private void verifyCnames(GslbRequest request, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(request);
    String cname = (context.platform + context.mtdBaseHost).toLowerCase();
    for (String alias : getAliases(request, context)) {
      alias = alias.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames != null && cnames.size() == 1 && cnames.get(0).canonical().equals(cname),
          "cname verify failed " + alias);
    }
  }

  private void verifyInfobloxDelete(GslbRequest request, VerifyContext context) throws Exception {
    InfobloxClient infobloxClient = getInfoBloxClient(request);
    for (String alias : getAliases(request, context)) {
      alias = alias.toLowerCase();
      List<CNAME> cnames = infobloxClient.getCNameRec(alias);
      verify(() ->cnames == null || cnames.isEmpty(), "cname delete verify failed " + alias);
    };
    verifyCloudCnameDelete(request, context, infobloxClient);
  }

  private void verifyCloudCnameDelete(GslbRequest request, VerifyContext context, InfobloxClient infobloxClient) throws Exception {
    String cloudCname = getCloudCname(request, context).toLowerCase();
    List<CNAME> cnames = infobloxClient.getCNameRec(cloudCname);
    verify(() ->cnames == null || cnames.isEmpty(), "cloud cname delete verify failed " + cloudCname);
  }

  private String getCloudCname(GslbRequest request, VerifyContext context) {
    String cloud = request.cloud().name();
    return context.platform + "." + context.subDomain + "." + cloud + "." + request.cloud().infobloxConfig().zone();
  }

  private List<String> getAliases(GslbRequest request, VerifyContext context) {
    List<String> list = new ArrayList<>();
    String suffix = getDomainSuffix(request, context);
    list.add(context.platform + suffix);

    String aliasesContent = request.fqdn().aliasesJson();
    if (StringUtils.isNotBlank(aliasesContent)) {
      JsonArray aliasArray = (JsonArray) jsonParser.parse(aliasesContent);

      for (JsonElement alias : aliasArray) {
        list.add(alias.getAsString() + suffix);
      }
    }

    aliasesContent = request.fqdn().fullAliasesJson();
    if (StringUtils.isNotBlank(aliasesContent)) {
      JsonArray aliasArray = (JsonArray) jsonParser.parse(aliasesContent);
      for (JsonElement alias : aliasArray) {
        list.add(alias.getAsString());
      }
    }
    return list;
  }

  private String getDomainSuffix(GslbRequest request, VerifyContext context) {
    return "." + context.subDomain + "." + request.cloud().infobloxConfig().zone();
  }

  private InfobloxClient getInfoBloxClient(GslbRequest request) {
    InfobloxConfig infobloxConfig = request.cloud().infobloxConfig();
    return InfobloxClient.builder().endPoint(infobloxConfig.host()).
        userName(infobloxConfig.user()).
        password(infobloxConfig.pwd()).
        tlsVerify(false).
        build();
  }

  private void verifyMtdDelete(VerifyContext context) throws Exception {
    TorbitClient client = context.torbitClient;
    TorbitApi torbit = context.torbit;
    Resp<MtdBaseResponse> resp = client.execute(torbit.getMTDBase(context.mtdBaseHost), MtdBaseResponse.class);
    logger.info(context.logKey + "verifying mtd host not exists ");
    if (resp.isSuccessful()) {
      logger.info(context.logKey + "mtd base exists, trying to get mtd host");
      MtdBase mtdBase = resp.getBody().mtdBase();
      Resp<MtdHostResponse> hostResp = client.execute(torbit.getMTDHost(mtdBase.mtdBaseId(), context.platform), MtdHostResponse.class);
      logger.info("hostResp response " + hostResp.getBody());
      verify(() -> !hostResp.isSuccessful(), "mtd host is not available");
    }
  }

  private void verifyMtdHost(GslbRequest request, GslbResponse response, VerifyContext context, boolean verifyResultEntries) throws Exception {
    TorbitClient client = context.torbitClient;
    TorbitApi torbit = context.torbit;

    logger.info(context.logKey + "verifying for platform " + context.platform);
    Resp<MtdBaseResponse> resp = client.execute(torbit.getMTDBase(context.mtdBaseHost), MtdBaseResponse.class);
    logger.info(context.logKey + "verifying mtd base ");
    verify(() -> resp.isSuccessful(), "mtd base exists");
    MtdBase mtdBase = resp.getBody().mtdBase();
    verify(() -> context.mtdBaseHost.equals(mtdBase.mtdBaseName()), "mtd base name match", context.mtdBaseHost, mtdBase.mtdBaseName());

    Resp<MtdHostResponse> hostResp = client.execute(torbit.getMTDHost(mtdBase.mtdBaseId(), context.platform), MtdHostResponse.class);
    logger.info(context.logKey + "verifying mtd host version exists");
    verify(() -> hostResp.isSuccessful(), "mtd host version exists");

    MtdHost host = hostResp.getBody().mtdHost();
    logger.info(context.logKey + "verifying mtd host targets");
    List<MtdTarget> targets = host.mtdTargets();
    logger.info(context.logKey + "configured mtd targets " + targets.stream().
        map(MtdTarget::mtdTargetHost).
        collect(Collectors.joining(",")));
    Map<String, MtdTarget> map = targets.stream().collect(Collectors.toMap(MtdTarget::mtdTargetHost, Function
        .identity()));
    List<Lb> lbList = getLbVips(request);
    logger.info(context.logKey + "expected targets " +
        lbList.stream().map(l -> l.vip).collect(Collectors.joining(",")));
    for (Lb lb : lbList) {
      verify(() -> map.containsKey(lb.vip), "lb vip present in MTD target");
      MtdTarget target = map.get(lb.vip);
      verify(() -> lb.isPrimary ? target.enabled() : !target.enabled(), "mtd target enabled/disabled based on cloud status");
    }
    context.primaryTargets = lbList.stream().filter(lb -> lb.isPrimary).map(lb -> lb.vip).collect(Collectors.toList());

    logger.info(context.logKey + "verifying mtd health checks");
    List<MtdHostHealthCheck> healthChecks = host.mtdHealthChecks();
    Map<Integer, EcvListener> expectedChecksMap = getHealthChecks(request, context).stream().
        collect(Collectors.toMap(e -> e.port, Function.identity()));
    logger.info(context.logKey + "expectedChecksMap : " + expectedChecksMap.size() + " " + expectedChecksMap);
    logger.info(context.logKey + "actual health checks : " + healthChecks);
    verify(() -> ((healthChecks != null ? healthChecks.size() : 0) ==
            (expectedChecksMap != null ? expectedChecksMap.size() : 0)),
        "all health checks are configured");
    if (healthChecks != null) {
      for (MtdHostHealthCheck healthCheck : healthChecks) {
        verify(() -> expectedChecksMap.containsKey(healthCheck.port()), "mtd health check available for port");
        EcvListener listener = expectedChecksMap.get(healthCheck.port());
        verify(() -> listener.protocol.equals(healthCheck.protocol()), "mtd health protocol matches");
        if (listener.ecv != null && !listener.ecv.isEmpty()) {
          verify(() -> listener.ecv.equals(healthCheck.testObjectPath()), "mtd health ecv matches");
        }
      }
    }
    if (verifyResultEntries)
      verify(() -> StringUtils.isNotBlank(response.getMtdBaseId()) &&
              StringUtils.isNotBlank(response.getGlb()) &&
              StringUtils.isNotBlank(response.getMtdDeploymentId()),
          "result ci contains gslb_map attribute");
  }

  private List<Lb> getLbVips(GslbRequest request) {
    List<DeployedLb> deployedLbs = request.deployedLbs();
    Map<Long, Cloud> cloudMap = request.platformClouds().stream().collect(Collectors.toMap(c -> c.id(), Function.identity()));
    List<Lb> list = deployedLbs.stream().map(lb -> getLbWithCloud(lb, cloudMap)).filter(lb -> StringUtils.isNotBlank(lb.vip)).collect(Collectors.toList());
    return list;
  }

  private Lb getLbWithCloud(DeployedLb deployedLb, Map<Long, Cloud> cloudMap) {
    Lb lb = new Lb();
    String lbName = deployedLb.name();
    String[] elements = lbName.split("-");
    String cloudId = elements[elements.length - 2];
    Cloud cloud = cloudMap.get(Long.parseLong(cloudId));
    lb.cloud = cloud.name();
    lb.vip = deployedLb.dnsRecord();
    lb.isPrimary = "1".equals(cloud.priority()) &&
        "active".equals(cloud.adminStatus4Platform()) ||
        "inactive".equals(cloud.adminStatus4Platform());
    return lb;
  }

  private List<EcvListener> getHealthChecks(GslbRequest request, VerifyContext context) {
    LbConfig lbConfig = request.lbConfig();
    List<EcvListener> ecvListeners = new ArrayList<>();
    String ecv = request.lbConfig().ecvMapJson();
    logger.info(context.logKey + "ecv_map " + ecv);
    JsonElement element = jsonParser.parse(ecv);
    JsonObject root = (JsonObject) element;
    Set<Entry<String, JsonElement>> set = root.entrySet();
    Map<Integer, String> ecvMap = set.stream().
        collect(Collectors.toMap(s -> Integer.parseInt(s.getKey()), s -> s.getValue().getAsString()));
    JsonArray listeners = (JsonArray) jsonParser.parse(lbConfig.listenerJson());
    logger.info(context.logKey + "listeners " + lbConfig.listenerJson());
    listeners.forEach(s -> {
      String listener = s.getAsString();
      String[] config = listener.split(" ");
      String protocol = config[0];
      int lbPort = Integer.parseInt(config[1]);
      int ecvPort = Integer.parseInt(config[config.length-1]);
      String healthConfig = ecvMap.get(ecvPort);
      if (healthConfig != null) {
        EcvListener ecvListener = new EcvListener();
        if ((protocol.startsWith("http"))) {
          String path = healthConfig.substring(healthConfig.indexOf(" ")+1);
          ecvListener.port = lbPort;
          ecvListener.protocol = protocol;
          ecvListener.ecv = path;
        }
        else {
          ecvListener.port = lbPort;
          ecvListener.protocol = protocol;
        }
        ecvListeners.add(ecvListener);
      }
    });
    return ecvListeners;
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

  private void loadContext(GslbRequest request, VerifyContext context) {
    String subdomain = request.customSubdomain();
    String baseGslbDomain = request.cloud().torbitConfig().gslbBaseDomain();
    context.subDomain = subdomain != null ? subdomain : request.environment() + "." + request.assembly() + "." + request.org();
    if (subdomain != null)
      context.mtdBaseHost = ("." + context.subDomain + "." + baseGslbDomain).toLowerCase();
    else
      context.mtdBaseHost = ("." + context.subDomain + "." + baseGslbDomain).toLowerCase();
  }


  private VerifyContext getContext(GslbRequest request) throws Exception {
    VerifyContext context = new VerifyContext();
    context.torbitClient = new TorbitClient(request.cloud().torbitConfig());
    context.torbit = context.torbitClient.getTorbit();
    loadContext(request, context);
    context.platform = request.platform().toLowerCase();
    return context;
  }

  class Lb {
    String vip;
    boolean isPrimary;
    String cloud;
  }

  class EcvListener {
    int port;
    String protocol;
    String ecv;

    public String toString() {
      return "[protocol: " +  protocol + ", port: " + port + ", ecv: " + ecv + "]";
    }
  }

  class VerifyContext {
    TorbitClient torbitClient;
    TorbitApi torbit;
    String mtdBaseHost;
    String platform;
    String logKey;
    String subDomain;
    List<String> primaryTargets;
  }

}
