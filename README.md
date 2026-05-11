# Nova POS - نظام نادل POS

تطبيق أندرويد لنظام نقاط البيع للمطاعم - نادل POS

## المواصفات
- **اسم التطبيق:** Nova POS
- **الحزمة:** com.nova.pos
- **الحد الأدنى:** Android 5.0 (API 21)
- **المستهدف:** Android 13.0 (API 33)
- **اللغة:** Java

## الميزات
- ✅ ماسح QR Code مدمج لقراءة رابط السيرفر
- ✅ إدخال يدوي لعنوان IP
- ✅ حفظ آخر IP تلقائياً
- ✅ WebView كامل الشاشة لواجهة النادل
- ✅ دعم HTTP للشبكات المحلية
- ✅ تبديل بين الكاميرا الأمامية والخلفية

## بناء APK عبر GitHub Actions

### الخطوات:
1. ارفع المشروع على GitHub
2. اذهب إلى **Actions** > **Build Nova POS APK**
3. اضغط **Run workflow**
4. انتظر اكتمال البناء وحمّل الـ APK من **Artifacts**

### ملاحظة مهمة - gradle-wrapper.jar:
قبل رفع المشروع، يجب إضافة ملف `gradle/wrapper/gradle-wrapper.jar`:
```bash
# من Android Studio: Build > Clean Project ثم رفع المشروع
# أو تحميله من:
# https://github.com/gradle/gradle/raw/v7.6.1/gradle/wrapper/gradle-wrapper.jar
```

## كيفية الاستخدام
1. افتح التطبيق وامنح صلاحية الكاميرا
2. امسح QR Code يحتوي على رابط السيرفر (مثل: 192.168.1.5:3000)
   أو أدخل الـ IP يدوياً واضغط "اتصال"
3. ستنتقل إلى واجهة النادل تلقائياً
