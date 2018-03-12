package com.oneops.gslb;

import static com.oneops.gslb.ContextFactory.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.oneops.gslb.domain.Action;
import com.oneops.gslb.domain.Fqdn;
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
    dnsHandler.jsonParser = new JsonParser();
    Gson gson = new Gson();
    dnsHandler.gson = gson;
    InfobloxClientProvider mock = mock(InfobloxClientProvider.class);
    dnsHandler.infobloxClientProvider = mock;
    when(mock.getInfobloxClient(any(), any(), any())).thenReturn(dnsMock);
  }

  @Test
  public void addCnames() {
    Context context = getContext(Action.add,"Plt", ".Env.a1.org.gslb.xyz.com", "Env.a1.org", "c1",
        Fqdn.create("[test1]", "[Test1.xyz.com]", "proximity"), null,
        "10.1.1.10", "prod.xyz.com", true, null);
    dnsHandler.setupDnsEntries(context);
    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(cnames.size(), 3);
    String cname = "plt.env.a1.org.gslb.xyz.com";
    assertEquals(cname, cnames.get("plt.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test1.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test1.xyz.com"));
    assertEquals("10.1.1.10", dnsMock.getNewArecs().get("plt.env.a1.org.c1.prod.xyz.com"));
  }

  @Test
  public void modifyCnames() {
    Context context = getContext(Action.update, "plt", ".env.a1.org.gslb.xyz.com", "env.a1.org", "c2",
        Fqdn.create("[test2]", "[test2.xyz.com]", "proximity"),
        Fqdn.create("[test1]", "[test1.xyz.com]", "proximity"),
        "10.1.1.20", "prod.xyz.com", true, null);
    dnsHandler.setupDnsEntries(context);
    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(cnames.size(), 3);
    String cname = "plt.env.a1.org.gslb.xyz.com";
    assertEquals(cname, cnames.get("plt.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test2.env.a1.org.prod.xyz.com"));
    assertEquals(cname, cnames.get("test2.xyz.com"));
    Set<String> delCnames = dnsMock.getDeleteCnames().stream().collect(Collectors.toSet());
    assertTrue(delCnames.contains("test1.env.a1.org.prod.xyz.com"));
    assertTrue(delCnames.contains("test1.xyz.com"));
    assertEquals("10.1.1.20", dnsMock.getNewArecs().get("plt.env.a1.org.c2.prod.xyz.com"));
  }

  @Test
  public void shutdownCloud() {
    Context context = getContext(Action.delete,"plt", ".env.a1.org.gslb.xyz.com", "env.a1.org",
        "c2", Fqdn.create("[test3]", "[test3.xyz.com]", "proximity"), null,
        "10.1.1.30", "prod.xyz.com", true, null);
    dnsHandler.setupDnsEntries(context);
    Map<String, String> cnames = dnsMock.getNewCnames();
    assertEquals(0, cnames.size());
    assertEquals(0, dnsMock.getDeleteCnames().size());
    List<String> arecs = dnsMock.getDeleteArecs();
    assertTrue(arecs.size() == 1 && "plt.env.a1.org.c2.prod.xyz.com".equals(arecs.get(0)));
    assertTrue(dnsMock.getDeleteCnames().isEmpty());
  }

  @Test
  public void platformDisable() {
    Context context = getContext(Action.delete,"Plt4", ".Env.a1.org.gslb.xyz.com", "Env.a1.org", "c2",
        Fqdn.create("[test4]", "[test4.xyz.com]", "proximity"), null,
        "10.1.1.40", "prod.xyz.com", false, null);
    dnsHandler.setupDnsEntries(context);
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
