package com.oneops.gslb;

import com.oneops.gslb.domain.Distribution;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.GslbResponse;
import com.oneops.gslb.domain.HealthCheck;
import com.oneops.gslb.domain.Lb;
import com.oneops.gslb.domain.Protocol;
import com.oneops.gslb.domain.ProvisionedGslb;
import com.oneops.gslb.domain.TorbitConfig;
import com.oneops.gslb.mtd.v2.domain.BaseResponse;
import com.oneops.gslb.mtd.v2.domain.CreateMtdBaseRequest;
import com.oneops.gslb.mtd.v2.domain.DataCenter;
import com.oneops.gslb.mtd.v2.domain.DataCentersResponse;
import com.oneops.gslb.mtd.v2.domain.DcCloud;
import com.oneops.gslb.mtd.v2.domain.MtdBase;
import com.oneops.gslb.mtd.v2.domain.MtdBaseHostRequest;
import com.oneops.gslb.mtd.v2.domain.MtdBaseHostResponse;
import com.oneops.gslb.mtd.v2.domain.MtdBaseRequest;
import com.oneops.gslb.mtd.v2.domain.MtdBaseResponse;
import com.oneops.gslb.mtd.v2.domain.MtdDeployment;
import com.oneops.gslb.mtd.v2.domain.MtdHost;
import com.oneops.gslb.mtd.v2.domain.MtdHostHealthCheck;
import com.oneops.gslb.mtd.v2.domain.MtdHostResponse;
import com.oneops.gslb.mtd.v2.domain.MtdTarget;
import com.oneops.gslb.mtd.v2.domain.ResponseError;
import com.oneops.gslb.mtd.v2.domain.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import retrofit2.Call;

public class MtdHandler {

  private static final String MTDB_TYPE_GSLB = "GSLB";

  private static final String MTD_BASE_EXISTS_ERROR = "DB_UNIQUENESS_VIOLATION";
  private static final String MTD_HOST_EXISTS_ERROR = "MTD_HOST_EXISTS_ON_MTD_BASE";
  private static final String MTD_HOST_NOT_EXISTS_ERROR = "COULD_NOT_FIND_MTD_HOST";

  private static final Logger logger = Logger.getLogger(MtdHandler.class);

  ConcurrentMap<String, DcCloud> cloudMap = new ConcurrentHashMap<>();

  private Map<Distribution, Integer> distributionMap;

  public MtdHandler() {
    distributionMap = new HashMap<>();
    distributionMap.put(Distribution.PROXIMITY, 0);
    distributionMap.put(Distribution.ROUND_ROBIN, 2);
  }

  public void setupTorbitGslb(Gslb gslb, ProvisionContext context) {
    String logKey = gslb.logContextId();
    try {
      initTorbitClient(gslb.subdomain(), gslb.torbitConfig().gslbBaseDomain(),
          logKey, gslb.torbitConfig(), context);
      logger.info(logKey + "MtdHandler setting up Mtd for Gslb");
      setupGslb(gslb, context);
    } catch(Exception e) {
      fail(context,"Exception performing setupTorbitGslb", e);
    }
  }

  private void setupGslb(Gslb gslb, ProvisionContext context) {
    try {
      MtdBase mtdBase = createMtdBaseWithRetry(gslb, context);
      if (mtdBase != null) {
        createMtdHost(gslb, context, mtdBase);
      }
      else {
        fail(context, "MtdBase could not be created", null);
      }
    } catch (Exception e) {
      fail(context, "Exception adding GSLB ", e);
    }
  }

  private void fail(ProvisionContext context, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(context.logKey() + failMsg, e);
    context.setProvisioningResponse(GslbProvisionResponse.failedResponse(failMsg));
  }

  private void fail(Context context, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(context.logKey() + failMsg, e);
    context.setResponse(GslbResponse.failedResponse(failMsg));
  }

  public void checkStatus(Gslb gslb, ProvisionContext context) {
    try {
      logger.info(context.logKey() + "checking mtd status");
      initTorbitClient(gslb.subdomain(), gslb.torbitConfig().gslbBaseDomain(),
          gslb.logContextId(), gslb.torbitConfig(), context);
      MtdBase mtdBase = getMtdBase(context);
      if (mtdBase == null) {
        fail(context, "mtd base could not be read", null);
        return;
      }

      logger.info(context.logKey() + "mtd base id : " + mtdBase.mtdBaseId());
      TorbitApi torbit = context.getTorbitClient().getTorbit();
      context.setApp(gslb.app().toLowerCase());
      Resp<MtdHostResponse> response = execute(context, torbit.getMTDHost(mtdBase.mtdBaseId(), context.getApp()), MtdHostResponse.class);
      if (!response.isSuccessful()) {
        fail(context, "could not get mtd host for this platform : " + response.getBody(), null);
        return;
      }

      boolean isMatching = false;
      MtdHostResponse hostResponse = response.getBody();
      List<MtdTarget> actualTargets = hostResponse.mtdHost().mtdTargets();
      logger.info(context.logKey() + "actual mtd targets " + actualTargets);
      List<MtdTarget> expectedTargetsList = getMtdTargets(gslb, context);
      logger.info(context.logKey() + "expected hosts " + expectedTargetsList);
      Map<String, MtdTarget> expectedMap = expectedTargetsList.stream().collect(Collectors.toMap(MtdTarget::mtdTargetHost, Function.identity()));

      if (expectedTargetsList.size() == actualTargets.size()) {
        Optional<MtdTarget> notMatching = actualTargets.stream().
            filter(t -> !expectedMap.containsKey(t.mtdTargetHost()) || !areTargetsSame(expectedMap.get(t.mtdTargetHost()), t)).
            findFirst();
        isMatching = !notMatching.isPresent();
      }

      if (isMatching) {
        logger.info(context.logKey() + "all mtd targets matching.");
      }
      else {
        fail(context, "mtd targets not matching", null);
      }
    } catch (Exception e) {
      fail(context, "exception checking gslb status", e);
    }
  }

  private boolean areTargetsSame(MtdTarget t1, MtdTarget t2) {
     return Objects.equals(t1.mtdTargetHost(), t2.mtdTargetHost()) && Objects.equals(t1.enabled(), t2.enabled()) &&
        Objects.equals(t1.cloudId(), t2.cloudId()) && Objects.equals(t1.dataCenterId(), t2.dataCenterId());
  }

  private <T extends BaseResponse> Resp<T> execute(Context context, Call<T> call, Class<T> respType) throws IOException, ExecutionException {
    return context.getTorbitClient().execute(call, respType);
  }

  private void loadDataCenters(ProvisionContext context) throws Exception {
    Resp<DataCentersResponse> response = execute(context, context.getTorbitApi().getDataCenters(), DataCentersResponse.class);
    if (response.isSuccessful()) {
      List<DataCenter> dataCenters= response.getBody().dataCenters();
      dataCenters.stream().flatMap(d -> d.clouds().stream()).forEach(c -> cloudMap.put(c.name(), c));
    }
    else {
      throw new ExecutionException("Failed while loading data centers " + getErrorMessages(response.getBody()));
    }
  }

  public void deleteGslb(ProvisionedGslb gslb, Context context) {
    String logKey = context.logKey();
    MtdBase mtdBase = null;
    context.setApp(gslb.app().toLowerCase());
    try {
      initTorbitClient(gslb.subdomain(), gslb.torbitConfig().gslbBaseDomain(),
          logKey, gslb.torbitConfig(), context);
    } catch (Exception e) {
      fail(context, "Exception getting torbit client to delete gslb ", e);
      return;
    }
    TorbitApi torbit = context.getTorbitClient().getTorbit();
    try {
      mtdBase = getMtdBase(context);
      if (mtdBase != null) {
        Resp<MtdBaseHostResponse> response = execute(context,
            torbit.deletetMTDHost(mtdBase.mtdBaseId(), context.getApp()), MtdBaseHostResponse.class);
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
        logger.info(logKey + "MtdBase not found for " + context.getMtdBaseName());
      }
    } catch (Exception e) {
      logger.error(logKey + "Exception deleting mtd host - " + e.getMessage(), e);
      if (mtdBase != null) {
        logger.error(logKey + "trying to get mtd host, if its already deleted we are good");
        //if the mtd host does not exist then it is fine
        try {
          Resp<MtdHostResponse> response = execute(context, torbit.getMTDHost(mtdBase.mtdBaseId(), context.getApp()), MtdHostResponse.class);
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

  MtdBaseHostRequest mtdBaseHostRequest(Gslb gslb, ProvisionContext context) throws Exception {
    List<MtdTarget> targets = getMtdTargets(gslb, context);
    if (targets != null) {
      context.setPrimaryTargets(targets.stream().filter(MtdTarget::enabled).map(MtdTarget::mtdTargetHost).collect(Collectors.toList()));
    }
    List<MtdHostHealthCheck> healthChecks = getHealthChecks(gslb);
    context.setApp(gslb.app().toLowerCase());
    logger.info(context.logKey() + "distribution config for fqdn : " + gslb.distribution());
    MtdHost mtdHost = MtdHost.create(context.getApp(), null, healthChecks, targets,
        true, 1, null, distributionMap.get(gslb.distribution()));
    return MtdBaseHostRequest.create(mtdHost);
  }


  private void createMtdHost(Gslb gslb, ProvisionContext context, MtdBase mtdBase) throws Exception {
    MtdBaseHostRequest mtdbHostRequest = mtdBaseHostRequest(gslb, context);
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

  private void updateExecutionResult(ProvisionContext context, MtdBase mtdBase, MtdBaseHostResponse response) {
    GslbProvisionResponse gslbResponse = context.getProvisioningResponse();
    gslbResponse.setMtdBaseId(Integer.toString(mtdBase.mtdBaseId()));
    Version version = response.version();
    if (version != null) {
      gslbResponse.setMtdVersion(Integer.toString(version.versionId()));
    }
    MtdDeployment deployment = response.deployment();
    if (deployment != null) {
      gslbResponse.setMtdDeploymentId(Integer.toString(deployment.deploymentId()));
    }
    String glb = context.getApp() + mtdBase.mtdBaseName();
    gslbResponse.setGlb(glb);
  }

  private MtdBaseHostResponse updateMtdHost(ProvisionContext context, MtdBaseHostRequest mtdbHostRequest, MtdBase mtdBase) throws Exception {
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

  private MtdBase getMtdBase(Context context) throws IOException, ExecutionException {
    MtdBase mtdBase = null;
    String mtdBaseHost = context.getMtdBaseName();
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

  private MtdBase createMtdBaseWithRetry(Gslb gslb, ProvisionContext context) throws Exception {
    int totalRetries = 2;
    MtdBase mtdBase = null;
    for (int retry = 0 ; mtdBase == null && retry < totalRetries ; retry++) {
      if (retry > 0) {
        Thread.sleep(3000);
      }
      try {
        mtdBase = createOrGetMtdBase(gslb, context);
      } catch (ExecutionException e) {
        e.printStackTrace();
        throw e;
      } catch (Exception e) {
        e.printStackTrace();
        logger.error(context.logKey() + "MtdBase creation failed for " + context.getMtdBaseName() + ", retry count " + retry, e);
      }
    }
    return mtdBase;
  }

  private MtdBase createOrGetMtdBase(Gslb gslb, ProvisionContext context) throws Exception {
    String logKey = context.logKey();
    CreateMtdBaseRequest request = CreateMtdBaseRequest.create(MtdBaseRequest.create(context.getMtdBaseName(), MTDB_TYPE_GSLB));
    logger.info(logKey + "MtdBase create request " + request);
    Resp<MtdBaseResponse> response = execute(context, context.getTorbitApi().createMTDBase(request, gslb.torbitConfig().groupId()), MtdBaseResponse.class);
    MtdBase mtdBase = null;
    if (!response.isSuccessful()) {
      MtdBaseResponse mtdBaseResponse = response.getBody();
      logger.info(logKey + "create MtdBase error response " + mtdBaseResponse);
      if (errorMatches(mtdBaseResponse.errors(), MTD_BASE_EXISTS_ERROR)) {
        logger.info(logKey + "create MtdBase failed with unique violation. try to get it.");
        //check if a MtdBase record exists already, probably created by another concurrent execution
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

  private MtdHostHealthCheck newHealthCheck(HealthCheck healthCheck) {
    String name = "gslb-" + healthCheck.protocol().toString() + "-" + healthCheck.port();
    if (healthCheck.protocol() == Protocol.HTTP) {
      return MtdHostHealthCheck.create(name, healthCheck.protocol().toString().toLowerCase(),
          healthCheck.port(), healthCheck.path(), null, 200,
          null, healthCheck.failureCountToMarkDown(), null, null, null, null,
          string(healthCheck.intervalInSecs()), string(healthCheck.retryDelayInSecs()), string(healthCheck.timeoutInSecs()));
    }
    else {
      return MtdHostHealthCheck.create(name, healthCheck.protocol().toString().toLowerCase(),
          healthCheck.port(), null, null, null,
          null, healthCheck.failureCountToMarkDown(), null, null, null, null,
          string(healthCheck.intervalInSecs()), string(healthCheck.retryDelayInSecs()), string(healthCheck.timeoutInSecs()));
    }
  }

  private String string(int value) {
    return Integer.toString(value) + "s";
  }

  List<MtdHostHealthCheck> getHealthChecks(Gslb gslb) {
    List<MtdHostHealthCheck> hcList = null;
    if (gslb.healthChecks() != null && !gslb.healthChecks().isEmpty()) {
      hcList = gslb.healthChecks().stream().map(this::newHealthCheck).collect(Collectors.toList());
    }
    if (hcList == null) {
      hcList = Collections.emptyList();
    }
    return hcList;
  }

  List<MtdTarget> getMtdTargets(Gslb gslb, ProvisionContext context) throws Exception {
    List<Lb> lbs = gslb.lbs();
    if (lbs != null && !lbs.isEmpty()) {

      List<MtdTarget> targetList = new ArrayList<>();
      for (Lb lb : lbs) {
        int weightPercent = 100;
        if (!lb.enabledForTraffic()) {
          weightPercent = 0;
        }
        addTarget(lb, context, ((lb.weightPercent() != null) ? lb.weightPercent() : weightPercent), targetList);
      }
      return targetList;
    }
    else {
      throw new ExecutionException("Can't get cloud VIPs from gslb request");
    }

  }

  private void addTarget(Lb lb, ProvisionContext context, Integer weightPercent, List<MtdTarget> targetList) throws Exception {
    String dnsRecord = lb.vip();
    if (StringUtils.isNotBlank(dnsRecord)) {
      if (!cloudMap.containsKey(lb.cloud())) {
        loadDataCenters(context);
      }
      DcCloud cloud = cloudMap.get(lb.cloud());
      logger.info("target dns record " + dnsRecord);
      targetList.add(MtdTarget.create(dnsRecord, cloud.dataCenterId(), cloud.id(), lb.enabledForTraffic(), weightPercent));
    }
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

  private void initTorbitClient(String subdomain, String gslbBaseDomain,
      String logKey, TorbitConfig torbitConfig, Context context) throws Exception {
    context.setMtdBaseName(("." + subdomain + "." + gslbBaseDomain).toLowerCase());
    TorbitClient client = new TorbitClient(torbitConfig);
    context.setTorbitClient(client);
    context.setTorbitApi(client.getTorbit());
    logger.info(logKey + "mtdBaseHost : " + context.getMtdBaseName());
  }


}
