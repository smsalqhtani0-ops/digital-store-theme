package com.hala.home;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.UserHandle;

/** One launchable app: an activity of a package, for one user profile. */
final class AppInfo {
    final ComponentName component;
    final UserHandle user;
    /** Stable id for saving layouts: `package/activity#userSerial`. */
    final String key;
    final String label;
    /** The label folded for search (lower case, Arabic letter forms unified). */
    final String searchLabel;
    final boolean system;
    Bitmap icon;

    AppInfo(ComponentName component, UserHandle user, long userSerial, String label, boolean system) {
        this.component = component;
        this.user = user;
        this.key = component.flattenToShortString() + "#" + userSerial;
        this.label = label;
        this.searchLabel = Search.fold(label + " " + component.getPackageName());
        this.system = system;
    }
}
