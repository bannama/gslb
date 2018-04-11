package com.oneops.gslb;

import static com.oneops.gslb.Requests.getProvisingRequest;
import static com.oneops.gslb.Requests.getProvisionContext;
import static com.oneops.gslb.Requests.getProvisionedGslb;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.oneops.gslb.domain.CloudARecord;
import com.oneops.gslb.domain.Gslb;
import com.oneops.gslb.domain.ProvisionedGslb;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class DnsHandlerTest {

  DnsHandler dnsHandler = new DnsHandler();
  DnsMock dnsMock = new DnsMock();

  @Before
  public void setup() {
    InfobloxClientProvider mock = mock(InfobloxClientProvider.class);
    dnsHandler.setInfobloxClientProvider(mock);
    when(mock.getInfobloxClient(any(), any(), any())).thenReturn(dnsMock);
  }

  @Test
  public void addCnames() {
    String cloud = "cl1";
    Gslb gslb = getProvisingRequest("Plt", "Env.a1.org", "10.1.1.10", cloud, "prod.xyz.com",
        null, Lists.newArrayList("Test1.xyz.com", "test1.env.a1.org.prod.xyz.com", "plt.env.a1.org.prod.xyz.com"),
        Collections.singletonList(CloudARecord.create(cloud, "plt.env.a1.org.c1.prod.xyz.com")),
        null, null);

    dnsHandler.setupDnsEntries(gslb, getProvisionContext("plt", ".env.a1.org.gslb.xyz.com"));
    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(cnames.size(), 3);
    String cname = "plt.env.a1.org.gslb.xyz.com";
    assertEquals(cname, cnames.get("plt.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test1.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test1.xyz.com"));
    assertThat(dnsMock.getNewArecs().size(), is(1));
    assertEquals("10.1.1.10", dnsMock.getNewArecs().get("plt.env.a1.org.c1.prod.xyz.com"));
  }


  @Test
  public void modifyCnames() {
    String cloud = "c2";
    Gslb gslb = getProvisingRequest("plt", "env.a1.org", "10.1.1.20", cloud, "prod.xyz.com",
        null, Lists.newArrayList("test2.xyz.com", "test2.env.a1.org.prod.xyz.com", "plt.env.a1.org.prod.xyz.com"),
        Collections.singletonList(CloudARecord.create(cloud, "plt.env.a1.org.c2.prod.xyz.com")),
        Lists.newArrayList("test1.xyz.com", "test1.env.a1.org.prod.xyz.com"), null);

    dnsHandler.setupDnsEntries(gslb, getProvisionContext("plt", ".env.a1.org.gslb.xyz.com"));

    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(cnames.size(), 3);
    String cname = "plt.env.a1.org.gslb.xyz.com";
    assertEquals(cname, cnames.get("plt.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test2.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test2.xyz.com"));
    Set<String> delCnames = dnsMock.getDeleteCnames().stream().collect(Collectors.toSet());
    assertTrue(delCnames.contains("test1.env.a1.org.prod.xyz.com"));
    assertTrue(delCnames.contains("test1.xyz.com"));
    assertThat(dnsMock.getNewArecs().size(), is(1));
    assertEquals("10.1.1.20", dnsMock.getNewArecs().get("plt.env.a1.org.c2.prod.xyz.com"));
  }


  @Test
  public void onlyObsoleteCloudEntries() {
    String cloud = "c2";
    Gslb gslb = getProvisingRequest("plt", "env.a1.org", "10.1.1.30", cloud, "prod.xyz.com",
        null, null,
        null,
        null,
        Collections.singletonList(CloudARecord.create(cloud, "plt.env.a1.org.c2.prod.xyz.com")));

    dnsHandler.setupDnsEntries(gslb, getProvisionContext("plt", ".env.a1.org.gslb.xyz.com"));

    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(0, cnames.size());
    assertEquals(0, dnsMock.getDeleteCnames().size());
    List<String> arecs = dnsMock.getDeleteArecs();
    assertTrue(arecs.size() == 1 && "plt.env.a1.org.c2.prod.xyz.com".equals(arecs.get(0)));
    assertTrue(dnsMock.getDeleteCnames().isEmpty());
  }

  @Test
  public void removeDnsEntries() {
    String cloud = "c2";
    ProvisionedGslb gslb = getProvisionedGslb("Plt4", "Env.a1.org", "10.1.1.40", null,
        Lists.newArrayList("test4.xyz.com", "test4.env.a1.org.prod.xyz.com", "plt4.env.a1.org.prod.xyz.com"),
        Collections.singletonList(CloudARecord.create(cloud, "plt4.env.a1.org.c2.prod.xyz.com")));

    dnsHandler.removeDnsEntries(gslb, getProvisionContext("plt4", ".env.a1.org.gslb.xyz.com"));

    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(cnames.size(), 0);
    Set<String> delCnames = dnsMock.getDeleteCnames().stream().collect(Collectors.toSet());
    assertTrue(delCnames.contains("test4.env.a1.org.prod.xyz.com"));
    assertTrue(delCnames.contains("test4.xyz.com"));
    assertTrue(delCnames.contains("plt4.env.a1.org.prod.xyz.com"));
    assertTrue(delCnames.size() == 3);
    List<String> arecs = dnsMock.getDeleteArecs();
    assertTrue(arecs.size() == 1 && "plt4.env.a1.org.c2.prod.xyz.com".equals(arecs.get(0)));
  }

}
