package gr.mpekiaris.launcher;
import android.view.View; import android.view.ViewGroup; import android.widget.TextView;
import org.junit.Test; import org.junit.runner.RunWith;
import org.robolectric.Robolectric; import org.robolectric.RobolectricTestRunner; import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class LaunchTest {
  void dump(View v, String ind) {
    String s = v.getClass().getSimpleName() + (v instanceof TextView ? " '" + ((TextView) v).getText() + "'" : "") + " vis=" + v.getVisibility();
    System.out.println(ind + s);
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) dump(((ViewGroup) v).getChildAt(i), ind + "  ");
  }
  @Test public void launches() {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    dump(a.getWindow().getDecorView(), "");
  }
}
