package org.mkonchady.mytripoo.views;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import org.mkonchady.mytripoo.R;
import org.mkonchady.mytripoo.utils.UtilsDate;
import org.mkonchady.mytripoo.utils.UtilsMisc;

/**
 * Custom implementation of the MarkerView.
 *
 * @author Philipp Jahoda
 */
public class BarMarkerView extends com.github.mikephil.charting.components.MarkerView {

    private final TextView tvContent;

    public BarMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent);
    }

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String yvalue = UtilsDate.getTimeDurationHHMMSS( (long) (e.getY()*1000), false);
        String xvalue = UtilsMisc.getDecimalFormat(e.getX(), 1, 1);
        String outline = "X: " + xvalue + " Y: " + yvalue;
        tvContent.setText(outline);
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}
