package com.oneops.gslb;


import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.oneops.infoblox.InfobloxClient;
import com.oneops.infoblox.model.a.ARec;
import com.oneops.infoblox.model.cname.CNAME;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DnsHandler {

  @Autowired
  JsonParser jsonParser;

  @Autowired
  Gson gson;

  @Autowired
  InfobloxClientProvider infobloxClientProvider;

  private static final Logger logger = Logger.getLogger(DnsHandler.class);

  private InfobloxClient getInfoBloxClient(Context context) throws ExecutionException {
    InfobloxConfig infobloxConfig = context.infobloxConfig();
    InfobloxClient client;
    if (isNotBlank(infobloxConfig.host()) && isNotBlank(infobloxConfig.user())) {
      client = infobloxClientProvider.getInfobloxClient(infobloxConfig.host(), infobloxConfig.user(), infobloxConfig.pwd());
    }
    else {
      throw new ExecutionException("Infoblox client could not be initialized. check cloud service configuration");
    }
    return client;
  }

  public void setupDnsEntries(Context context) {
    logger.info(context.logKey() + "setting up cnames");
    InfobloxClient infoBloxClient;
    try {
      infoBloxClient = getInfoBloxClient(context);
    } catch (ExecutionException e) {
      fail(context, "Failed while initializing infoblox client", e);
      return;
    }

    Set<String> currentAliases = new HashSet<>();
    Fqdn fqdn = context.getRequest().fqdn();
    String defaultAlias = getFullAlias(context.getRequest().platform(), context);
    currentAliases.add(defaultAlias);

    addAlias(fqdn.aliasesJson(), currentAliases, t -> (getFullAlias(t, context)));
    addAlias(fqdn.fullAliasesJson(), currentAliases, Function.identity());

    if (context.getRequest().action() == Action.DELETE) {
      if (context.getRequest().platformEnabled()) {
        logger.info(context.logKey() + "deleting all cnames as platform is getting disabled");
        deleteCNames(context, currentAliases, infoBloxClient);
      }
      else {
        logger.info(context.logKey() + "platform is not disabled, deleting only cloud cname");
      }
      deleteCloudEntry(context, infoBloxClient);
    }
    else {
      Set<String> oldAliases = new HashSet<>();
      Fqdn oldFqdn = context.getRequest().oldFqdn();
      if (oldFqdn != null) {
        addAlias(oldFqdn.aliasesJson(), oldAliases, t -> (getFullAlias(t, context)));
        addAlias(oldFqdn.fullAliasesJson(), oldAliases, Function.identity());
      }
      List<String> aliasesToRemove = oldAliases.stream().filter(a -> !currentAliases.contains(a)).collect(Collectors.toList());
      deleteCNames(context, aliasesToRemove, infoBloxClient);
      Map<String, String> entriesMap = new HashMap<>();
      addCnames(context, currentAliases, infoBloxClient, entriesMap);
      addCloudEntry(context, infoBloxClient, entriesMap);
      if (context.getResponse().getStatus() != Status.FAILED) {
        updateWoResult(entriesMap, context);
      }
    }
  }

  private void fail(Context context, String message, Exception e) {
    String failMsg = (e != null) ? message + " : " + e.getMessage() : message;
    logger.error(context.logKey() + failMsg, e);
    context.setResponse(GslbResponse.failedResponse(failMsg));
  }


  private void deleteCloudEntry(Context context, InfobloxClient infobloxClient) {
    String cloudEntry = getCloudDnsEntry(context);
    logger.info(context.logKey() + "deleting cloud dns entry " + cloudEntry);
    try {
      infobloxClient.deleteARec(cloudEntry);
    } catch(Exception e) {
      fail(context,"Exception while deleting cloud dns entry ", e);
    }
  }

  private Long getCloudIdFromLbName(DeployedLb deployedLb) {
    String[] elements = deployedLb.name().split("-");
    return Long.parseLong(elements[elements.length-2]);
  }

  private void addCloudEntry(Context context, InfobloxClient infobloxClient, Map<String, String> entriesMap) {
    String cloudEntry = getCloudDnsEntry(context);
    Optional<DeployedLb> opt = context.getRequest().deployedLbs().stream().
        filter(lb -> getCloudIdFromLbName(lb) == context.getRequest().cloud().id()).findFirst();
    if (opt.isPresent()) {
      String lbVip = opt.get().dnsRecord();
      logger.info(context.logKey() + "cloud dns entry " + cloudEntry + " lbVip " + lbVip);

      if (isNotBlank(lbVip)) {
        entriesMap.put(cloudEntry, lbVip);
        try {
          List<ARec> records = infobloxClient.getARec(cloudEntry);
          if (records != null && records.size() == 1) {
            if (lbVip.equals(records.get(0).ipv4Addr())) {
              logger.info(context.logKey() + "cloud dns entry is already set, not doing anything");
              return;
            }
            else {
              logger.info(context.logKey() + "cloud dns entry already exists, but not matching");
            }
          }

          logger.info(context.logKey() + "cloud dns entry: " + cloudEntry + ", deleting the current entry and recreating it");
          List<String> list = infobloxClient.deleteARec(cloudEntry);
          logger.info(context.logKey() + "infoblox deleted cloud entries count " + list.size());
          logger.info(context.logKey() + "creating cloud dns entry " + cloudEntry);
          ARec aRecord = infobloxClient.createARec(cloudEntry, lbVip);
          logger.info(context.logKey() + "arecord created " + aRecord);

        } catch (IOException e) {
          fail(context,"Exception while setting up cloud dns entry ", e);
        }
      }
    }


  }

  private void updateWoResult(Map<String, String> entriesMap, Context context) {
    GslbResponse response = context.getResponse();
    response.setDnsEntries(entriesMap);
    String domainName = context.platform() + context.getMtdBaseHost();
    entriesMap.put(domainName, context.getPrimaryTargets() != null ? context.getPrimaryTargets().toString() : "");
  }

  private String getFullAlias(String alias, Context context) {
    return String.join(".", alias, context.getSubDomain(), context.infobloxConfig().zone());
  }

  private String getCloudDnsEntry(Context context) {
    return String.join(".", context.platform(), context.getSubDomain(),
        context.cloud().name(), context.infobloxConfig().zone()).toLowerCase();
  }

  private void addCnames(Context context, Collection<String> aliases,
      InfobloxClient infoBloxClient, Map<String, String> entriesMap) {
    String cname = (context.platform() + context.getMtdBaseHost()).toLowerCase();
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
          continue;
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
            if (e.getMessage() != null && e.getMessage().contains("IBDataConflictError")) {
              logger.info(context.logKey() + "ignoring add cname error");
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

  private void deleteCNames(Context context, Collection<String> aliases, InfobloxClient infoBloxClient) {
    List<String> aliasList = aliases.stream().map(String::toLowerCase).collect(Collectors.toList());
    logger.info(context.logKey() + "delete cnames " + aliasList);
    aliasList.stream().forEach(
        a -> {
          try {
            infoBloxClient.deleteCNameRec(a);
          } catch(Exception e) {
            if (e.getCause() != null && e.getCause().getMessage() != null
                && e.getCause().getMessage().contains("AdmConDataNotFoundError")) {
              logger.info(context.logKey() + "delete failed with no data found for " + a + ", ignore and continue");
            }
            else {
              fail(context, "Failed while deleting cname " + a, e);
            }

          }
        }
    );
  }

  private void addAlias(String attrValue, Set<String> aliases, Function<String, String> mapper) {
    if (isNotBlank(attrValue)) {
      JsonArray aliasArray = (JsonArray) jsonParser.parse(attrValue);
      for (JsonElement alias : aliasArray) {
        aliases.add(mapper.apply(alias.getAsString()));
      }
    }
  }

}
