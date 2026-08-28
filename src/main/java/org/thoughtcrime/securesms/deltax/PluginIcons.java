package org.thoughtcrime.securesms.deltax;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;

/**
 * Builds circular plugin icons: the bundled logo when present, otherwise a gray circle with the
 * ic_advanced glyph.
 */
public final class PluginIcons {

  private static final int GRAY = 0xFF9E9E9E;
  private static Drawable defaultIcon;

  private PluginIcons() {}

  public static Drawable getIcon(Context context, PluginInfo plugin) {
    Bitmap logo = plugin.getLogoBitmap(context);
    if (logo != null) return circular(context, logo);
    return getDefault(context);
  }

  public static Drawable getDefault(Context context) {
    if (defaultIcon != null) return defaultIcon;
    int size = Math.round(context.getResources().getDisplayMetrics().density * 96f);
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(GRAY);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    Drawable icon = context.getDrawable(R.drawable.ic_advanced_white_24dp);
    if (icon != null) {
      int inset = size / 4;
      icon.setBounds(inset, inset, size - inset, size - inset);
      icon.draw(canvas);
    }
    RoundedBitmapDrawable drawable =
        RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
    drawable.setCircular(true);
    drawable.setAntiAlias(true);
    defaultIcon = drawable;
    return drawable;
  }

  private static Drawable circular(Context context, Bitmap bitmap) {
    RoundedBitmapDrawable drawable =
        RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
    drawable.setCircular(true);
    drawable.setAntiAlias(true);
    return drawable;
  }
}
