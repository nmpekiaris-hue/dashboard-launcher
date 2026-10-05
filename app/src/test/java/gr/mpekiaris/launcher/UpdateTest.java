package gr.mpekiaris.launcher;
import android.content.Context; import android.view.*; import android.widget.*;
import okhttp3.mockwebserver.*; import okio.Buffer;
import org.junit.Test; import org.junit.runner.RunWith;
import org.robolectric.Robolectric; import org.robolectric.RobolectricTestRunner; import org.robolectric.RuntimeEnvironment; import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*; import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class UpdateTest {
  void collect(View v, List<String> out) { if (v instanceof TextView && v.getVisibility()==View.VISIBLE) out.add(((TextView)v).getText().toString()); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) collect(((ViewGroup)v).getChildAt(i), out); }
  @Test public void findsAndDownloadsUpdate() throws Exception {
    MockWebServer srv = new MockWebServer();
    final String base = "http://127.0.0.1:" + 0;
    srv.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest r) {
      if (r.getPath().startsWith("/version.json")) return new MockResponse().setBody("{\"versionCode\":99,\"versionName\":\"9.9\",\"notes\":\"Δοκιμή\",\"apk\":\"http://127.0.0.1:" + r.getRequestUrl().port() + "/app.apk\"}");
      if (r.getPath().startsWith("/app.apk")) return new MockResponse().setBody(new Buffer().write(new byte[5000]));
      return new MockResponse().setResponseCode(404);
    }});
    srv.start();
    Context ctx = RuntimeEnvironment.getApplication();
    ctx.getSharedPreferences("dash", 0).edit().putString("upd_url", "http://127.0.0.1:" + srv.getPort() + "/version.json").commit();
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    for (int i = 0; i < 30; i++) { Thread.sleep(100); shadowOf(a.getMainLooper()).idle(); }
    List<String> t = new ArrayList<>(); collect(a.getWindow().getDecorView(), t);
    System.out.println("UPD " + t.subList(0, Math.min(4, t.size())));
    boolean found = false; for (String s : t) if (s.contains("Νέα έκδοση 9.9") && s.contains("Δοκιμή")) found = true;
    assertTrue(found);
    srv.shutdown();
  }
}
