package com.hala.home;

import java.text.Normalizer;
import java.util.Locale;

/** Search folding: case, accents and the Arabic letter forms people type interchangeably. */
final class Search {
    private Search() {}

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ':
                    b.append('ا');
                    break;
                case 'ى':
                    b.append('ي');
                    break;
                case 'ة':
                    b.append('ه');
                    break;
                case 'ؤ':
                    b.append('و');
                    break;
                case 'ئ':
                    b.append('ي');
                    break;
                case 'ـ': // tatweel
                    break;
                default:
                    // Combining marks (accents, Arabic short vowels) are dropped.
                    int type = Character.getType(c);
                    if (type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK) {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }

    static boolean matches(AppInfo app, String foldedQuery) {
        if (foldedQuery.isEmpty()) {
            return true;
        }
        for (String part : foldedQuery.trim().split("\\s+")) {
            if (!app.searchLabel.contains(part)) {
                return false;
            }
        }
        return true;
    }
}
