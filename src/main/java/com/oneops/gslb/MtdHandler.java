package com.oneops.gslb;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oneops.gslb.v2.domain.BaseResponse;
import com.oneops.gslb.v2.domain.DcCloud;
import com.oneops.gslb.v2.domain.CreateMtdBaseRequest;
import com.oneops.gslb.v2.domain.DataCenter;
import com.oneops.gslb.v2.domain.DataCentersResponse;
import com.oneops.gslb.v2.domain.MtdBase;
import com.oneops.gslb.v2.domain.MtdBaseHostRequest;
import com.oneops.gslb.v2.domain.MtdBaseHostResponse;
import com.oneops.gslb.v2.domain.MtdBaseRequest;
import com.oneops.gslb.v2.domain.MtdBaseResponse;
import com.oneops.gslb.v2.domain.MtdDeployment;
import com.oneops.gslb.v2.domain.MtdHost;
import com.oneops.gslb.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.v2.domain.MtdHostResponse;
import com.oneops.gslb.v2.domain.MtdTarget;
import com.oneops.gslb.v2.domain.ResponseError;
import com.oneops.gslb.v2.domain.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

@Component
public class MtdHandler {

  private static final String MTDB_TYPE_GSLB = "GSLB";

  private static final String CLOUD_STATUS_ACTIVE = "active";
  private static final String CLOUD_STATUS_INACTIVE = "inactive";

  private static final String MTD_BASE_EXISTS_ERROR = "DB_UNIQUENESS_VIOLATION";
  private static final String MTD_HOST_EXISTS_ERROR = "MTD_HOST_EXISTS_ON_MTD_BASE";
  private static final String MTD_HOST_NOT_EXISTS_ERROR = "COULD_NOT_FIND_MTD_HOST";

  private static final Logger logger = Logger.getLogger(MtdHandler.class);

  ConcurrentMap<String, DcCloud> cloudMap = new ConcurrentHashMap<>();

  @Value("${mtd.timeout:2s}")
  String timeOut;

  @Value("${mtd.inteval:5s}")
  String interval;

  @Value("${mtd.retryDelay:30s}")
  String retryDelay;

  @Value("${mtd.failsForDown:3}")
  int failureCountToMarkDown;

  @Autowired
  Gson gson;

  @Autowired
  JsonParser jsonParser;

  @PostConstruct
  public void init() {
   logger.info("initialized with mtd timeout: " + timeOut + ", interval: " + interval +
       ", retryDelay: " + retryDelay + ", failureCountToMarkDown: " + failureCountToMarkDown);
  }

  public void setupTorbitGdns(Context context) {
    GslbRequest request = context.getRequest();
    String logKey = request.logContextId();
    try {
      initTorbitClient(context);
      logger.info(logKey + "MtdHandler setting up Mtd for Gslb");
      switch(request.action()) {
        case ADD:
          addGslb(context);
          break;
        case DELETE:
          logger.info(logKey + "handling delete rfc action");
          //delete mtd host only if it is a platform disable
          if (!request.platformEnabled()) {
            logger.info(logKey + "platform getting disabled, removing mtd host");
            deleteGslb(context);
          }
          else {
            logger.info(logKey + "platform is not disabled, continuing with update mtd");
            updateGslb(context);
          }
          break;
        default:
          //update/replace actions
          updateGslb(context);
      }
    } catch(Exception e) {
      e.printStackTrace();
      fail(context,"Exception performing " + request.action() + " GSLB ", e);
    }
  }

  private void fail(Context context, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(context.logKey() + failMsg, e);
    context.setResponse(GslbResponse.failedResponse(failMsg));
  }

  private <T extends BaseResponse> Resp<T> execute(Context context, Call<T> call, Class<T> respType) throws IOException, ExecutionException {
    return context.getTorbitClient().execute(call, respType);
  }

  private void loadDataCenters(Context context) throws Exception {
    Resp<DataCentersResponse> response = execute(context, context.getTorbitApi().getDataCenters(), DataCentersResponse.class);
    if (response.isSuccessful()) {
      List<DataCenter> dataCenters= response.getBody().dataCenters();
      dataCenters.stream().flatMap(d -> d.clouds().stream()).forEach(c -> cloudMap.put(c.name(), c));
    }
    else {
      throw new ExecutionException("Failed while loading data centers " + getErrorMessages(response.getBody()));
    }
  }

  private void deleteGslb(Context context) {
    String logKey = context.logKey();
    TorbitApi torbit = context.getTorbitClient().getTorbit();
    MtdBase mtdBase = null;
    String platform = context.platform();
    try {
      mtdBase = getMtdBase(context);
      if (mtdBase != null) {
        Resp<MtdBaseHostResponse> response = execute(context, torbit.deletetMTDHost(mtdBase.mtdBaseId(), platform), MtdBaseHostResponse.class);
        if (response.isSuccessful()) {
          MtdBaseHostResponse hostResponse = response.getBody();
          logger.info(logKey + "delete MtdHost response " + hostResponse);
        }
        else {
          MtdBaseHostResponse errorResp = response.getBody();
          logger.info(context.logKey() + "delete MtdHost response code " + response.getCode() + " message " + response.getBody());
          if (errorMatches(errorResp.errors(), MTD_HOST_NOT_EXISTS_ERROR)) {
            logger.info(logKey + "MtdHost does not exist.");
          }
          else {
            String error = getErrorMessage(errorResp.errors());
            logger.info(logKey + "deleteMtdHost failed with  error " + error);
            fail(context, "delete operation failed", null);
          }
        }
      }
      else {
        logger.info(logKey + "MtdBase not found for " + context.getMtdBaseHost());
      }
    } catch (Exception e) {
      logger.error(logKey + "Exception deleting mtd host - " + e.getMessage(), e);
      if (mtdBase != null) {
        logger.error(logKey + "trying to get mtd host, if its already deleted we are good");
        //if the mtd host does not exist then it is fine
        try {
          Resp<MtdHostResponse> response = execute(context, torbit.getMTDHost(mtdBase.mtdBaseId(), platform), MtdHostResponse.class);
          if (!response.isSuccessful() && errorMatches(response.getBody().errors(), MTD_HOST_NOT_EXISTS_ERROR)) {
            return;
          }
        } catch (Exception e1) {
          logger.error(logKey + "Exception while getting mtd host", e);
        }
      }
      fail(context, "Exception deleting GSLB ", e);
    }
  }

  private void updateGslb(Context context) {
    try {
      MtdBase mtdBase = getMtdBase(context);
      if (mtdBase != null) {
        updateMtdHost(context, mtdBase);
      }
      else {
        fail(context, "MtdBase doesn't exist", null);
      }
    } catch (Exception e) {
      fail(context, "Exception updating GSLB ", e);
    }
  }

  private void addGslb(Context context) {
    try {
      MtdBase mtdBase = createMtdBaseWithRetry(context);
      if (mtdBase != null) {
        createMTDHost(context, mtdBase);
      }
      else {
        fail(context, "MtdBase could not be created", null);
      }
    } catch (Exception e) {
      fail(context, "Exception adding GSLB ", e);
    }
  }

  private MtdBaseHostRequest mtdBaseHostRequest(Context context) throws Exception {
    List<MtdTarget> targets = getMtdTargets(context);
    if (targets != null) {
      context.setPrimaryTargets(targets.stream().filter(MtdTarget::enabled).map(MtdTarget::mtdTargetHost).collect(Collectors.toList()));
    }
    List<MtdHostHealthCheck> healthChecks = getHealthChecks(context);
    MtdHost mtdHost = MtdHost.create(context.platform(), null, healthChecks, targets,
        true, 1, null);
    return MtdBaseHostRequest.create(mtdHost);
  }


  private void createMTDHost(Context context, MtdBase mtdBase) throws Exception {
    MtdBaseHostRequest mtdbHostRequest = mtdBaseHostRequest(context);
    String logKey = context.logKey();
    logger.info(logKey + "create host request " + mtdbHostRequest);
    Resp<MtdBaseHostResponse> response = execute(context, context.getTorbitApi().createMTDHost(mtdbHostRequest, mtdBase.mtdBaseId()), MtdBaseHostResponse.class);
    MtdBaseHostResponse hostResponse = response.getBody();
    if (!response.isSuccessful()) {
      logger.info(logKey + "create MtdHost error response " + hostResponse);
      if (errorMatches(hostResponse.errors(), MTD_HOST_EXISTS_ERROR)) {
        logger.info(logKey + "MtdHost already existing, so trying to update");
        hostResponse = updateMtdHost(context, mtdbHostRequest, mtdBase);
      }
      else {
        String error = getErrorMessage(hostResponse.errors());
        throw new ExecutionException("createMtdHost failed with " + error);
      }
    }
    else {
      logger.info(logKey + "create MtdHost response  " + hostResponse);
    }
    if (hostResponse != null) {
      updateExecutionResult(context, mtdBase, hostResponse);
    }
  }

  private void updateExecutionResult(Context context, MtdBase mtdBase, MtdBaseHostResponse response) {
    GslbResponse gslbResponse = context.getResponse();
    gslbResponse.setMtdBaseId(Integer.toString(mtdBase.mtdBaseId()));
    Version version = response.version();
    if (version != null) {
      gslbResponse.setMtdVersion(Integer.toString(version.versionId()));
    }
    MtdDeployment deployment = response.deployment();
    if (deployment != null) {
      gslbResponse.setMtdDeploymentId(Integer.toString(deployment.deploymentId()));
    }
    String glb = context.platform() + mtdBase.mtdBaseName();
    gslbResponse.setGlb(glb);
    context.setResponse(gslbResponse);
  }

  private MtdBaseHostResponse updateMtdHost(Context context, MtdBaseHostRequest mtdbHostRequest, MtdBase mtdBase) throws Exception {
    logger.info(context.logKey() + " update host request " + mtdbHostRequest);
    Resp<MtdBaseHostResponse> response = execute(context,
        context.getTorbitApi().updateMTDHost(mtdbHostRequest, mtdBase.mtdBaseId(), mtdbHostRequest.mtdHost().mtdHostName()), MtdBaseHostResponse.class);
    MtdBaseHostResponse hostResponse = response.getBody();
    if (response.isSuccessful()) {
      logger.info(context.logKey() + "update MtdHost response " + hostResponse);
      return hostResponse;
    }
    else {
      logger.info(context.logKey() + "update MtdHost response code " + response.getCode() + " message " + hostResponse);
      String error = getErrorMessage(hostResponse.errors());
      throw new ExecutionException("updateMtdHost failed with " + error);
    }
  }

  private void updateMtdHost(Context context, MtdBase mtdBase) throws Exception {
    MtdBaseHostRequest mtdbHostRequest = mtdBaseHostRequest(context);
    MtdBaseHostResponse hostResponse = updateMtdHost(context, mtdbHostRequest, mtdBase);
    updateExecutionResult(context, mtdBase, hostResponse);
  }

  private MtdBase getMtdBase(Context context) throws IOException, ExecutionException {
    MtdBase mtdBase = null;
    String mtdBaseHost = context.getMtdBaseHost();
    Resp<MtdBaseResponse> response = execute(context, context.getTorbitApi().getMTDBase(mtdBaseHost), MtdBaseResponse.class);
    if (!response.isSuccessful()) {
      logger.info(context.logKey() + "MtdBase could not be read for " + mtdBaseHost + " error " + getErrorMessages(response.getBody()));
    }
    else {
      mtdBase = response.getBody().mtdBase();
    }
    logger.info(context.logKey() + "mtdBase for host " + mtdBaseHost + " " + mtdBase);
    return mtdBase;
  }

  private MtdBase createMtdBaseWithRetry(Context context) throws Exception {
    int totalRetries = 2;
    MtdBase mtdBase = null;
    for (int retry = 0 ; mtdBase == null && retry < totalRetries ; retry++) {
      if (retry > 0) {
        Thread.sleep(5000);
      }
      try {
        mtdBase = createOrGetMtdBase(context);
      } catch (ExecutionException e) {
        e.printStackTrace();
        throw e;
      } catch (Exception e) {
        e.printStackTrace();
        logger.error(context.logKey() + "MtdBase creation failed for " + context.getMtdBaseHost() + ", retry count " + retry, e);
      }
    }
    return mtdBase;
  }

  private MtdBase createOrGetMtdBase(Context context) throws Exception {
    String logKey = context.logKey();
    CreateMtdBaseRequest request = CreateMtdBaseRequest.create(MtdBaseRequest.create(context.getMtdBaseHost(), MTDB_TYPE_GSLB));
    logger.info(logKey + "MtdBase create request " + request);
    Resp<MtdBaseResponse> response = execute(context, context.getTorbitApi().createMTDBase(request, context.torbitConfig().groupId()), MtdBaseResponse.class);
    MtdBase mtdBase = null;
    if (!response.isSuccessful()) {
      MtdBaseResponse mtdBaseResponse = response.getBody();
      logger.info(logKey + "create MtdBase error response " + mtdBaseResponse);
      if (errorMatches(mtdBaseResponse.errors(), MTD_BASE_EXISTS_ERROR)) {
        logger.info(logKey + "create MtdBase failed with unique violation. try to get it.");
        //check if a MtdBase record exists already, probably created by another FQDN instance running parallel
        mtdBase = getMtdBase(context);
      }
      else {
        logger.info(logKey + "create MtdBase request failed with unknown error");
      }
    }
    else {
      logger.info(logKey + "MtdBase create response " + response);
      mtdBase = response.getBody().mtdBase();
    }
    return mtdBase;
  }

  List<MtdHostHealthCheck> getHealthChecks(Context context) {
    List<MtdHostHealthCheck> hcList = new ArrayList<>();
    LbConfig lbConfig = context.getRequest().lbConfig();
    if (lbConfig != null) {

      if (StringUtils.isNotBlank(lbConfig.listenerJson()) && StringUtils.isNotBlank(lbConfig.ecvMapJson())) {
        JsonElement element = jsonParser.parse(lbConfig.ecvMapJson());

        if (element instanceof JsonObject) {
          JsonObject root = (JsonObject) element;
          Set<Entry<String, JsonElement>> set = root.entrySet();
          Map<Integer, String> ecvMap = set.stream().
              collect(Collectors.toMap(s -> Integer.parseInt(s.getKey()), s -> s.getValue().getAsString()));
          logger.info(context.logKey() + "listeners " + lbConfig.listenerJson());
          JsonArray listeners = (JsonArray) jsonParser.parse(lbConfig.listenerJson());

          listeners.forEach(s -> {
            String listener = s.getAsString();
            //listeners are generally in this format 'http <lb-port> http <app-port>', gslb needs to use the lb-port for health checks
            //ecv map is configured as '<app-port> : <ecv-url>', so we need to use the app-port from listener configuration to lookup the ecv config from ecv map
            String[] config = listener.split(" ");
            if (config.length >= 2) {
              String protocol = config[0];
              int lbPort = Integer.parseInt(config[1]);
              int ecvPort = Integer.parseInt(config[config.length-1]);

              String healthConfig = ecvMap.get(ecvPort);
              if (healthConfig != null) {
                if ((protocol.startsWith("http"))) {
                  String path = healthConfig.substring(healthConfig.indexOf(" ") + 1);
                  logger.info(context.logKey() + "healthConfig : " + healthConfig + ", health check configuration, protocol: " + protocol + ", port: " + lbPort
                      + ", path " + path);
                  MtdHostHealthCheck healthCheck = newHealthCheck("gslb-" + protocol + "-" + lbPort,
                      protocol, lbPort, path, 200);
                  hcList.add(healthCheck);

                } else if ("tcp".equals(protocol)) {
                  logger.info(
                      context.logKey() + "health check configuration, protocol: " + protocol + ", port: " + lbPort);
                  hcList.add(
                      newHealthCheck("gslb-" + protocol + "-" + lbPort, protocol, lbPort, null, null));
                }
              }
            }
          });
        }
      }
    }
    return hcList;
  }

  private MtdHostHealthCheck newHealthCheck(String name, String protocol, int port, String testObjectPath, Integer expectedStatus) {
    return MtdHostHealthCheck.create(name, protocol, port, testObjectPath, null, expectedStatus,
        null, failureCountToMarkDown, true, null, null, null, interval, retryDelay, timeOut);
  }

  List<MtdTarget> getMtdTargets(Context context) throws Exception {
    List<LbCloud> lbClouds = getLbCloudMerged(context);

    if (lbClouds != null && !lbClouds.isEmpty()) {
      Map<Integer, List<LbCloud>> map = lbClouds.stream().collect(Collectors.groupingBy(l -> l.isPrimary ? 1 : 2));
      List<LbCloud> primaryClouds = map.get(1);
      List<LbCloud> secondaryClouds = map.get(2);
      Fqdn fqdn = context.getRequest().fqdn();
      String distribution = fqdn.distribution();
      boolean isProximity = "proximity".equals(distribution);
      logger.info(context.logKey() + "distribution config for fqdn : " + distribution);
      Integer weight = null;
      if (!isProximity) {
        weight = 100;
      }

      List<MtdTarget> targetList = new ArrayList<>();
      if (primaryClouds != null) {
        for (LbCloud lbCloud : primaryClouds) {
          addTarget(lbCloud, context, true, weight, targetList);
        }
      }

      if (secondaryClouds != null) {
        for (LbCloud lbCloud : secondaryClouds) {
          addTarget(lbCloud, context, false, 0, targetList);
        }
      }
      return targetList;
    }
    else {
      throw new ExecutionException("Can't get cloud VIPs from workorder");
    }

  }

  private void addTarget(LbCloud lbCloud, Context context, Boolean enabled, Integer weightPercent, List<MtdTarget> targetList) throws Exception {
    if (StringUtils.isNotBlank(lbCloud.dnsRecord)) {
      if (!cloudMap.containsKey(lbCloud.cloud)) {
        loadDataCenters(context);
      }
      DcCloud cloud = cloudMap.get(lbCloud.cloud);
      logger.info("target dns record " + lbCloud.dnsRecord);
      targetList.add(MtdTarget.create(lbCloud.dnsRecord, cloud.dataCenterId(), cloud.id(), enabled, weightPercent));
    }
  }

  private List<LbCloud> getLbCloudMerged(Context context) throws Exception {
    List<DeployedLb> lbs = context.getRequest().deployedLbs();
    List<Cloud> clouds = context.getRequest().platformClouds();
    if (clouds != null && !clouds.isEmpty()) {
      Map<Long, Cloud> cloudMap = clouds.stream().collect(Collectors.toMap(c -> c.id(), Function.identity()));
      return lbs.stream().map(lb -> getLbWithCloud(lb, cloudMap)).collect(Collectors.toList());
    }
    else {
      String message = "platform clouds not available in request";
      logger.error(context.logKey() + message);
      throw new ExecutionException(message);
    }
  }

  private LbCloud getLbWithCloud(DeployedLb lb, Map<Long, Cloud> cloudMap) {
    LbCloud lc = new LbCloud();
    String lbName = lb.name();
    String[] elements = lbName.split("-");
    String cloudId = elements[elements.length - 2];
    Cloud cloud = cloudMap.get(Long.parseLong(cloudId));
    lc.cloud = cloud.name();
    lc.dnsRecord = lb.dnsRecord();

    lc.isPrimary = "1".equals(cloud.priority()) &&
        (CLOUD_STATUS_ACTIVE.equals(cloud.adminStatus4Platform()) ||
            CLOUD_STATUS_INACTIVE.equals(cloud.adminStatus4Platform()));
    return lc;
  }

  private String getErrorMessages(BaseResponse response) {
    if (response != null) {
      return getErrorMessage(response.errors());
    }
    return null;
  }

  private String getErrorMessage(List<ResponseError> errors) {
    String message = null;
    if (errors != null) {
      message = errors.stream().map(ResponseError::errorCode).collect(Collectors.joining(" | "));
    }
    return message != null ? message : "unknown error";
  }

  private boolean errorMatches(List<ResponseError> responseError, String error) {
    if (responseError != null) {
      Optional<ResponseError> matchinError = responseError.stream().
          filter(r -> error.equals(r.errorCode())).findFirst();
      if (matchinError.isPresent()) {
        return true;
      }
    }
    return false;
  }

  private void initTorbitClient(Context context) throws Exception {
    context.setMtdBaseHost(("." + context.getRequest().customSubdomain() + "." + context.torbitConfig().gslbBaseDomain()).toLowerCase());
    TorbitClient client = new TorbitClient(context.torbitConfig());
    context.setTorbitClient(client);
    context.setTorbitApi(client.getTorbit());
    logger.info(context.logKey() + "mtdBaseHost : " + context.getMtdBaseHost());
  }


  class LbCloud {
    String dnsRecord;
    String cloud;
    boolean isPrimary;
  }

}
