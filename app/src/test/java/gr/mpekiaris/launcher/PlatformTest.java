package gr.mpekiaris.launcher;
import android.content.Context; import android.view.*; import android.widget.*;
import okhttp3.mockwebserver.*;
import org.junit.Test; import org.junit.runner.RunWith;
import org.robolectric.Robolectric; import org.robolectric.RobolectricTestRunner; import org.robolectric.RuntimeEnvironment; import org.robolectric.annotation.Config;
import java.net.InetSocketAddress; import java.nio.charset.StandardCharsets; import java.util.*;
import static org.junit.Assert.*; import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class PlatformTest {
  static String lastPost = "";
  void collect(View v, List<String> out) { if (v instanceof TextView) out.add(((TextView)v).getText().toString()); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) collect(((ViewGroup)v).getChildAt(i), out); }
  @Test public void loginAndCalendar() throws Exception {
    MockWebServer srv = new MockWebServer();
    srv.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest r) {
      String path = r.getPath(); String ck = r.getHeader("Cookie");
      if (path.startsWith("/api/login")) return new MockResponse().addHeader("Set-Cookie", "sid=abc123; Path=/; HttpOnly").addHeader("Content-Type","application/json").setBody("{\"user\":{}}");
      if (path.startsWith("/api/calendar") && "POST".equals(r.getMethod())) { lastPost = r.getBody().readUtf8(); return new MockResponse().setBody("{\"ok\":true}"); }
      if (ck == null || !ck.contains("sid=abc123")) return new MockResponse().setResponseCode(401).setBody("{\"error\":\"x\"}");
      return new MockResponse().addHeader("Content-Type","application/json").setBody("{\"events\":[{\"kind\":\"todo\",\"title\":\"Προσφορά Λαμία\",\"resp\":\"Νίκος\",\"priority\":\"high\"},"+
        "{\"kind\":\"event\",\"title\":\"Ραντεβού ΔΕΔΔΗΕ\",\"time\":\"10:30\",\"mine\":true,\"id\":7},"+
        "{\"kind\":\"sched\",\"edge\":\"start\",\"title\":\"Ηλεκτρολογικά\",\"store\":\"ΛΑΜΙΑ 1\",\"proj\":\"Πυρασφάλεια\"}]}");
    }});
    srv.start();
    Context ctx = RuntimeEnvironment.getApplication();
    ctx.getSharedPreferences("dash", 0).edit().putString("plat_base", "http://127.0.0.1:" + srv.getPort()).commit();
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    List<String> t = new ArrayList<>(); collect(a.getWindow().getDecorView(), t);
    assertTrue(t.contains("Σύνδεση στην πλατφόρμα ΜΠΕΚΙΑΡΗΣ"));
    java.lang.reflect.Method login = MainActivity.class.getDeclaredMethod("platLogin", String.class, String.class); login.setAccessible(true);
    login.invoke(a, "nikos", "pw");
    for (int i = 0; i < 40; i++) { Thread.sleep(100); shadowOf(a.getMainLooper()).idle(); }
    t = new ArrayList<>(); collect(a.getWindow().getDecorView(), t);
    System.out.println("TEXTS " + t);
    assertTrue(t.contains("Ραντεβού ΔΕΔΔΗΕ")); assertTrue(t.contains("Προσφορά Λαμία")); assertTrue(t.contains("▶ Έναρξη"));
    assertTrue(t.indexOf("Ραντεβού ΔΕΔΔΗΕ") < t.indexOf("Προσφορά Λαμία"));
    java.lang.reflect.Method add = MainActivity.class.getDeclaredMethod("platAdd", String.class, String.class, boolean.class); add.setAccessible(true);
    add.invoke(a, "Αυτοψία", "09:00", true);
    for (int i = 0; i < 20; i++) { Thread.sleep(100); shadowOf(a.getMainLooper()).idle(); }
    System.out.println("POST " + lastPost);
    assertTrue(lastPost.contains("Αυτοψία") && lastPost.contains("\"shared\":true"));
    assertEquals("09:30", MainActivity.normTime("930")); assertEquals("09:00", MainActivity.normTime("9")); assertEquals("14:05", MainActivity.normTime("14.05")); assertNull(MainActivity.normTime("25:00"));
    srv.shutdown();
  }
}
