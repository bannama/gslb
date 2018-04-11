package com.oneops.gslb;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.oneops.gslb.domain.CloudARecord;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.GslbProvisionResponse;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.Lb;
import com.oneops.gslb.domain.ProvisionedGslb;
import com.oneops.infoblox.InfobloxClient;
import com.oneops.infoblox.model.a.ARec;
import com.oneops.infoblox.model.cname.CNAME;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class DnsHandler {

  private InfobloxClientProvider infobloxClientProvider = new InfobloxClientProvider();

  private static final Logger logger = Logger.getLogger(DnsHandler.class);

  private InfobloxClient getInfoBloxClient(InfobloxConfig infobloxConfig) throws ExecutionException {
    InfobloxClient client;
    if (infobloxConfig != null &&
        isNotBlank(infobloxConfig.host()) && isNotBlank(infobloxConfig.user())) {
      client = infobloxClientProvider.getInfobloxClient(infobloxConfig.host(), infobloxConfig.user(), infobloxConfig.pwd());
    }
    else {
      throw new ExecutionException("Infoblox client could not be initialized. check infoblox configuration");
    }
    return client;
  }

  public void setupDnsEntries(Gslb gslb, ProvisionContext context) {
    logger.info(context.logKey() + "setting up cnames");
    try {
      InfobloxClient infoBloxClient = getInfoBloxClient(gslb.infobloxConfig());
      setupCnames(gslb, context, infoBloxClient);
      deleteObsoleteEntries(gslb, context, infoBloxClient);
    } catch (Exception e) {
      handleException(context, e, "Failed while setting up dns entries");
    }
  }

  public void removeDnsEntries(ProvisionedGslb gslb, Context context) {
    try {
      InfobloxClient infoBloxClient = getInfoBloxClient(gslb.infobloxConfig());
      deleteCNames(context, gslb.cnames(), infoBloxClient);
      deleteCloudEntries(context, gslb.cloudARecords(), infoBloxClient);
    } catch(Exception e) {
      handleException(context, e, "Failed while removing dns entries");
    }
  }

  public void checkStatus(Gslb gslb, ProvisionContext context) {
    try {
      InfobloxClient infoBloxClient = getInfoBloxClient(gslb.infobloxConfig());
      checkStatus(gslb, context, infoBloxClient);
    } catch(Exception e) {
      handleException(context, e, "Failed while checking status for dns entries");
    }
  }

  private void handleException(Context context, Exception e, String message) {
    if (context.getResponse().getStatus() != Status.FAILED) {
      failSimple(context, message, e);
    }
  }

  private void deleteObsoleteEntries(Gslb gslb, ProvisionContext context, InfobloxClient infobloxClient) throws Exception {
    deleteCNames(context, gslb.obsoleteCnames(), infobloxClient);
    deleteCloudEntries(context, gslb.obsoleteCloudARecords(), infobloxClient);
  }

  private void checkStatus(Gslb gslb, ProvisionContext context, InfobloxClient infoBloxClient) {
    String cname = context.getApp() + context.getMtdBaseName();
    logger.info(context.logKey() + "checking if cnames exist : ");
    List<String> aliasList = gslb.cnames().stream().map(String::toLowerCase).collect(Collectors.toList());
    logger.info(context.logKey() + "expected aliases " + aliasList + ", cname : " + cname);
    for (String alias : aliasList) {
      try {
        List<CNAME> existingCnames = infoBloxClient.getCNameRec(alias);
        if (existingCnames == null || existingCnames.isEmpty() || !cname.equals(existingCnames.get(0).canonical())) {
          fail(context, "cname not created properly " + alias, null);
          break;
        }
      } catch(Exception e) {
        failSimple(context, "Exception while checking cnames ", e);
      }
    }
  }

  private void setupCnames(Gslb gslb, ProvisionContext context, InfobloxClient infoBloxClient) throws Exception {
    Map<String, String> entriesMap = new HashMap<>();
    addCnames(gslb, context, infoBloxClient, entriesMap);
    addCloudEntry(gslb, context, infoBloxClient, entriesMap);
    updateWoResult(entriesMap, context);
  }

  private void fail(Context context, String message, Exception e) throws Exception {
    failSimple(context, message, e);
    if (e == null) {
      e = new ExecutionException(message);
    }
    throw e;
  }

  private void failSimple(Context context, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(context.logKey() + failMsg, e);
    context.failedResponseWithMessage(failMsg);
  }

  private void addCloudEntry(Gslb gslb, ProvisionContext context, InfobloxClient infobloxClient,
      Map<String, String> entriesMap) throws Exception {
    if (gslb.cloudARecords() != null) {
      Map<String, Lb> cloudToLbMap = gslb.lbs().stream().collect(Collectors.toMap(c -> c.cloud(), Function.identity()));
      Map<String, String> cloudEntries = new HashMap<>();
      for (CloudARecord cloudARecord : gslb.cloudARecords()) {

        if (cloudToLbMap.containsKey(cloudARecord.cloud())) {
          Lb lb = cloudToLbMap.get(cloudARecord.cloud());
          if (lb != null && StringUtils.isNotBlank(lb.vip())) {
            cloudEntries.put(cloudARecord.aRecord(), lb.vip());
          }
        }
      }
      for (Entry<String, String> entry : cloudEntries.entrySet()) {
        addCloudARecord(context, entry.getKey(), entry.getValue(), infobloxClient);
      }
      entriesMap.putAll(cloudEntries);
    }
  }

  private void addCloudARecord(ProvisionContext context, String cloudEntry, String lbVip, InfobloxClient infobloxClient) throws Exception {
    if (isNotBlank(lbVip)) {
      logger.info(context.logKey() + "cloud dns entry " + cloudEntry + " lbVip " + lbVip);
      try {
        boolean alreadyExists = false;
        List<ARec> records = infobloxClient.getARec(cloudEntry);
        if (records != null && records.size() == 1) {
          if (lbVip.equals(records.get(0).ipv4Addr())) {
            logger.info(context.logKey() + "cloud dns entry is already set, not doing anything");
            return;
          }
          else {
            alreadyExists = true;
            logger.info(context.logKey() + "cloud dns entry already exists, but not matching");
          }
        }

        if (alreadyExists) {
          logger.info(context.logKey() + "cloud dns entry: " + cloudEntry + ", deleting the current entry and recreating it");
          List<String> list = infobloxClient.deleteARec(cloudEntry);
          logger.info(context.logKey() + "infoblox deleted cloud entries count " + list.size());
        }
        logger.info(context.logKey() + "creating cloud dns entry " + cloudEntry);
        ARec aRecord = infobloxClient.createARec(cloudEntry, lbVip);
        logger.info(context.logKey() + "arecord created " + aRecord);

      } catch (IOException e) {
        fail(context,"Exception while setting up cloud dns entry ", e);
      }
    }
  }

  private void updateWoResult(Map<String, String> entriesMap, ProvisionContext context) {
    GslbProvisionResponse response = context.getProvisioningResponse();
    response.setDnsEntries(entriesMap);
    String domainName = context.getApp() + context.getMtdBaseName();
    entriesMap.put(domainName, context.getPrimaryTargets() != null ? context.getPrimaryTargets().toString() : "");
  }

  private void addCnames(Gslb gslb, ProvisionContext context, InfobloxClient infoBloxClient, Map<String, String> entriesMap) throws Exception {
    List<String> aliases = gslb.cnames();
    if (aliases != null) {
      String cname = context.getApp() + context.getMtdBaseName();
      List<String> aliasList = aliases.stream().map(String::toLowerCase).collect(Collectors.toList());
      logger.info(context.logKey() + "aliases to be added/updated " + aliasList + ", cname : " + cname);
      for (String alias : aliasList) {
        try {
          entriesMap.put(alias, cname);
          List<CNAME> existingCnames = infoBloxClient.getCNameRec(alias);
          if (existingCnames != null && !existingCnames.isEmpty()) {
            if (cname.equals(existingCnames.get(0).canonical())) {
              //cname matches, no need to do anything
              logger.info(context.logKey() + "cname already exists, no change needed " + alias);
            }
            else {
              fail(context, "alias " + alias + " exists already with a different cname", null);
            }
          }
          else {
            logger.info(context.logKey() + "cname not found, trying to add " + alias);
            try {
              CNAME newCname = infoBloxClient.createCNameRec(alias, cname);
              if (newCname == null || !cname.equals(newCname.canonical())) {
                fail(context, "Failed to create cname ", null);
              }
              else {
                logger.info(context.logKey() + "cname added successfully " + alias);
              }
            } catch (IOException e) {
              logger.error(context.logKey() + "cname [" + alias + "] creation failed with " + e.getMessage());
              if (isAlreadyExistsError(alias, e)) {
                logger.info(context.logKey() + "ignoring add cname error - record already exists");
              }
              else {
                logger.error(e);
                fail(context, "Failed while adding cname " +  cname, e);
              }
            }
          }
        } catch (IOException e) {
          fail(context, "Failed while adding/updating cnames ", e);
        }
      }
    }
  }

  private boolean isAlreadyExistsError(String alias, IOException e) {
    return e.getMessage() != null &&
        e.getMessage().contains(String.format("IBDataConflictError: IB.Data.Conflict:The record '%s' already exists.", alias));
  }

  private void deleteCNames(Context context, List<String> aliases, InfobloxClient infoBloxClient) throws Exception {
    if (aliases != null) {
      List<String> aliasList = aliases.stream().map(String::toLowerCase).collect(Collectors.toList());
      logger.info(context.logKey() + "delete cnames " + aliasList);
      for (String alias : aliasList) {
        try {
          infoBloxClient.deleteCNameRec(alias);
        } catch(Exception e) {
          if (e.getCause() != null && e.getCause().getMessage() != null
              && e.getCause().getMessage().contains("AdmConDataNotFoundError")) {
            logger.info(context.logKey() + "delete failed with no data found for " + alias + ", ignore and continue");
          }
          else {
            fail(context, "Failed while deleting cname " + alias, e);
          }
        }
      }
    }
  }

  private void deleteCloudEntries(Context context, List<CloudARecord> aRecords, InfobloxClient infobloxClient)
      throws Exception {
    if (aRecords != null) {
      for (CloudARecord aRecord : aRecords) {
        logger.info(context.logKey() + "deleting cloud dns entry " + aRecord.aRecord());
        try {
          infobloxClient.deleteARec(aRecord.aRecord());
        } catch(Exception e) {
          fail(context,"Exception while deleting cloud dns entry ", e);
        }
      }
    }
  }

  public InfobloxClientProvider getInfobloxClientProvider() {
    return infobloxClientProvider;
  }

  public void setInfobloxClientProvider(InfobloxClientProvider infobloxClientProvider) {
    this.infobloxClientProvider = infobloxClientProvider;
  }

}
