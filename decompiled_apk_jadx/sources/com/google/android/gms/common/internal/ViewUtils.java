package com.google.android.gms.common.internal;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public class ViewUtils {
    private ViewUtils() {
    }

    public static String getXmlAttributeString(String namespace, String name, Context context, AttributeSet attrs, boolean allowResources, boolean required, String logTag) {
        String attributeValue = attrs == null ? null : attrs.getAttributeValue(namespace, name);
        if (attributeValue != null && attributeValue.startsWith("@string/") && allowResources) {
            String substring = attributeValue.substring(8);
            String packageName = context.getPackageName();
            TypedValue typedValue = new TypedValue();
            try {
                Resources resources = context.getResources();
                resources.getValue(packageName + ":string/" + substring, typedValue, true);
            } catch (Resources.NotFoundException e) {
                Log.w(logTag, "Could not find resource for " + name + ": " + attributeValue);
            }
            if (typedValue.string != null) {
                attributeValue = typedValue.string.toString();
            } else {
                String obj = typedValue.toString();
                Log.w(logTag, "Resource " + name + " was not a string: " + obj);
            }
        }
        if (required && attributeValue == null) {
            Log.w(logTag, "Required XML attribute \"" + name + "\" missing");
        }
        return attributeValue;
    }
}
