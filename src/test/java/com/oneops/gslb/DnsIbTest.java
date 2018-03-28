package com.oneops.gslb;

import static com.oneops.gslb.ContextFactory.getContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.oneops.gslb.domain.Action;
import com.oneops.gslb.domain.Fqdn;
import com.oneops.gslb.domain.InfobloxConfig;
import org.junit.Before;
import org.junit.Test;

public class DnsIbTest {

  DnsHandler dnsHandler = new DnsHandler();

  @Before
  public void setup() {
    dnsHandler.jsonParser = new JsonParser();
    Gson gson = new Gson();
    dnsHandler.gson = gson;
    dnsHandler.infobloxClientProvider = new InfobloxClientProvider();
  }

  @Test
  public void shouldFailInvalidDomainNameCreation() {
    InfobloxConfig config = null;
    try {
      config = InfobloxConfig
          .create(getEnv("iba_host"), getEnv("iba_user"), getEnv("iba_password"),
              getEnv("iba_invalid_zone"));
    } catch (Exception e) {
    }
    assumeTrue(config != null);
    Context context = getContext(Action.add, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c2",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    assertThat(context.getResponse().getStatus(), is(Status.FAILED));
  }

  @Test
  public void shouldProceedForDuplicateDomainNameCreation() {
    InfobloxConfig config = null;
    try {
      config = InfobloxConfig
          .create(getEnv("iba_host"), getEnv("iba_user"), getEnv("iba_password"),
              getEnv("iba_valid_zone"));
    } catch (Exception e) {
    }
    assumeTrue(config != null);
    Context context = getContext(Action.add, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c2",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    assertThat(context.getResponse().getStatus(), not(Status.FAILED));
    dnsHandler.setupDnsEntries(context);
    assertThat(context.getResponse().getStatus(), not(Status.FAILED));
    context = getContext(Action.delete, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c2",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
  }

  @Test
  public void statusCheckAfterAdding() {
    InfobloxConfig config = null;
    try {
      config = InfobloxConfig
          .create(getEnv("iba_host"), getEnv("iba_user"), getEnv("iba_password"),
              getEnv("iba_valid_zone"));
    } catch (Exception e) {
    }
    assumeTrue(config != null);
    Context context = getContext(Action.add, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c3",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    context = getContext(Action.gslbstatus, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c3",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    assertThat(context.getResponse().getStatus(), not(Status.FAILED));
    context = getContext(Action.delete, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c3",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
  }

  @Test
  public void failStatusCheckForDifferentMtdHost() {
    InfobloxConfig config = null;
    try {
      config = InfobloxConfig
          .create(getEnv("iba_host"), getEnv("iba_user"), getEnv("iba_password"),
              getEnv("iba_valid_zone"));
    } catch (Exception e) {
    }
    assumeTrue(config != null);
    Context context = getContext(Action.add, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c4",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    context = getContext(Action.gslbstatus, "p1", ".e1.a1.org.gslb.x.oneops.com", "e1.a1.org", "c4",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
    assertThat(context.getResponse().getStatus(), is(Status.FAILED));
    context = getContext(Action.delete, "p1", ".e1.a1.org.gslb.oneops.com", "e1.a1.org", "c3",
        Fqdn.create("[]", "[]", "proximity"), null,
        "10.1.1.40", config.zone(), false, config);
    dnsHandler.setupDnsEntries(context);
  }

  private String getEnv(String envName) {
    String val = System.getProperty(envName, System.getenv(envName));
    return val;
  }

}
