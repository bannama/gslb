package com.oneops.gslb;

import static com.oneops.gslb.Requests.getContext;
import static com.oneops.gslb.Requests.getProvisingRequest;
import static com.oneops.gslb.Requests.getProvisionContext;
import static com.oneops.gslb.Requests.getProvisionedGslb;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.InfobloxConfig;
import com.oneops.gslb.domain.ProvisionedGslb;
import java.util.Collections;
import org.junit.Test;

public class DnsIbTest {

  DnsHandler dnsHandler = new DnsHandler();

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
    Gslb gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "p1", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    ProvisionContext context = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.setupDnsEntries(gslb, context);
    assertThat(context.getResponse().getStatus(), is(Status.FAILED));
  }

  private String getEnv(String envName) {
    String val = System.getProperty(envName, System.getenv(envName));
    return val;
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
    Gslb gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "p1", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    ProvisionContext provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.setupDnsEntries(gslb, provisionContext);

    assertThat(provisionContext.getResponse().getStatus(), not(Status.FAILED));
    provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.setupDnsEntries(gslb, provisionContext);
    assertThat(provisionContext.getResponse().getStatus(), not(Status.FAILED));

    ProvisionedGslb provisionedGslb = getProvisionedGslb("p1", "e1.a1.org", config.zone(), config,
        Collections.singletonList("p1.e1.a1.org." + config.zone()), null);
    Context context = getContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.removeDnsEntries(provisionedGslb, context);
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
    Gslb gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "c3", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    ProvisionContext provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.setupDnsEntries(gslb, provisionContext);

    gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "c3", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.checkStatus(gslb, provisionContext);

    ProvisionedGslb provisionedGslb = getProvisionedGslb("p1", "e1.a1.org", config.zone(), config,
        Collections.singletonList("p1.e1.a1.org." + config.zone()), null);
    Context context = getContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.removeDnsEntries(provisionedGslb, context);
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

    Gslb gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "c3", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    ProvisionContext provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.setupDnsEntries(gslb, provisionContext);

    gslb = getProvisingRequest("p1", "e1.a1.org","10.1.1.40",
        "c3", config.zone(), config, Collections.singletonList("p1.e1.a1.org." + config.zone()),
        null, null, null);
    provisionContext = getProvisionContext("p1", ".e1.a1.org.gslb.x.oneops.com");
    dnsHandler.checkStatus(gslb, provisionContext);
    assertThat(provisionContext.getResponse().getStatus(), is(Status.FAILED));

    ProvisionedGslb provisionedGslb = getProvisionedGslb("p1", "e1.a1.org", config.zone(), config,
        Collections.singletonList("p1.e1.a1.org." + config.zone()), null);
    Context context = getContext("p1", ".e1.a1.org.gslb.oneops.com");
    dnsHandler.removeDnsEntries(provisionedGslb, context);
  }

}
