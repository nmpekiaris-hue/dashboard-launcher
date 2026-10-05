package gr.mpekiaris.launcher;
import android.view.*; import android.widget.*; import android.view.inputmethod.EditorInfo;
import org.junit.Test; import org.junit.runner.RunWith;
import org.robolectric.Robolectric; import org.robolectric.RobolectricTestRunner; import org.robolectric.annotation.Config;
import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class FlowTest {
  void collect(View v, List<View> out) { out.add(v); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) collect(((ViewGroup)v).getChildAt(i), out); }
  List<View> all(MainActivity a){ List<View> l=new ArrayList<>(); collect(a.getWindow().getDecorView(), l); return l; }
  @Test public void flows() {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    List<EditText> eds = new ArrayList<>(); for (View v: all(a)) if (v instanceof EditText) eds.add((EditText)v);
    eds.get(0).setText("τηλ"); eds.get(0).onEditorAction(EditorInfo.IME_ACTION_GO);
    EditText todo = eds.get(1); todo.setText("Προσφορά Λαμία"); todo.onEditorAction(EditorInfo.IME_ACTION_DONE);
    for (View v: all(a)) if (v instanceof TextView && "Προσφορά Λαμία".equals(((TextView)v).getText().toString())) { ((View)v.getParent()).performClick(); System.out.println("TODO toggled"); break; }
    for (View v: all(a)) if (v instanceof TextView && "Εφαρμογές".equals(((TextView)v).getText().toString())) { ((View)v.getParent()).performClick(); System.out.println("ALL apps clicked"); }
    a.onBackPressed();
    for (View v: all(a)) if (v instanceof TextView && ((TextView)v).getText().toString().contains("Λαμία")) System.out.println("FOUND " + ((TextView)v).getText());
    System.out.println("FLOW OK");
  }
}
