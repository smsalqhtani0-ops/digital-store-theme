package com.tabletunlock;

// يعمل داخل عملية shell (uid 2000) التي يشغّلها Shizuku.
interface IShellService {
    // رقم المعاملة هذا محجوز من Shizuku لإيقاف الخدمة.
    void destroy() = 16777114;

    // ينفّذ أمراً مسموحاً بدون shell (بدون sh -c) ويعيد رمز الخروج.
    int exec(in String[] args) = 1;

    // مخرجات آخر أمر فشل (لا تحتوي الوسائط المرسلة).
    String lastError() = 2;
}
