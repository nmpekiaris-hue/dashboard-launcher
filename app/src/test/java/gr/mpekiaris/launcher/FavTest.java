package gr.mpekiaris.launcher;
import android.content.Intent; import android.content.pm.*; import android.view.*; import android.widget.*;
import org.junit.Test; import org.junit.runner.RunWith;
import org.robolectric.Robolectric; import org.robolectric.RobolectricTestRunner; import org.robolectric.RuntimeEnvironment; import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*; import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class FavTest {
  void collect(View v, List<String> out) { if (v instanceof TextView && v.getVisibility()==View.VISIBLE) out.add(((TextView)v).getText().toString()); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) collect(((ViewGroup)v).getChildAt(i), out); }
  ResolveInfo ri(String pkg, String label) { ResolveInfo r = new ResolveInfo(); r.activityInfo = new ActivityInfo(); r.activityInfo.packageName = pkg; r.activityInfo.name = pkg + ".Main"; r.activityInfo.applicationInfo = new ApplicationInfo(); r.activityInfo.applicationInfo.packageName = pkg; r.nonLocalizedLabel = label; return r; }
  @Test public void pinsApps() throws Exception {
    PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (String[] x : new String[][]{{"com.viber.voip","Viber"},{"com.google.android.gm","Gmail"},{"com.autodesk.autocad","AutoCAD"}}) shadowOf(pm).addResolveInfoForIntent(i, ri(x[0], x[1]));
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    List<String> t = new ArrayList<>(); collect(a.getWindow().getDecorView(), t);
    assertTrue(t.contains("ΕΦΑΡΜΟΓΕΣ"));
    java.lang.reflect.Field f = MainActivity.class.getDeclaredField("apps"); f.setAccessible(true);
    List<?> apps = (List<?>) f.get(a);
    java.lang.reflect.Method add = MainActivity.class.getDeclaredMethod("addFav", MainActivity.App.class); add.setAccessible(true);
    add.invoke(a, apps.get(2)); add.invoke(a, apps.get(0));
    t = new ArrayList<>(); collect(a.getWindow().getDecorView(), t);
    System.out.println("FAV " + t.subList(t.indexOf("ΕΦΑΡΜΟΓΕΣ"), t.indexOf("ΗΜΕΡΟΛΟΓΙΟ")));
    int v = t.indexOf("Viber"), c = t.indexOf("AutoCAD");
    assertTrue(v > 0 && c > 0 && v < t.indexOf("ΗΜΕΡΟΛΟΓΙΟ"));
  }
}
