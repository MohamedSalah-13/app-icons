# app-icons

مكتبة Java صغيرة تكتب ملفات أيقونة البرنامج كلها **من رسم واحد**، وتحمّلها على نوافذ JavaFX.
كل برنامج يحتفظ برسمه فقط؛ كتابة `.ico` و `.icns` وصور PNG تُكتب مرة واحدة هنا بدل نسخها في كل مشروع.

- **وقت تعديل الأيقونة** (`com.hamza.icons`): ترسم كل مقاس مستقلًا ثم تكتب ملف Windows ‏(`.ico`)،
  وملف macOS ‏(`.icns`)، وصورة Linux، وصور نوافذ البرنامج.
- **وقت التشغيل** (`com.hamza.icons.fx`): تضع الأيقونة بكل مقاساتها على النافذة والحوارات.
- بلا أي اعتماديات. Java 21 فأحدث. النصفان اختياريان: من يستخدم أحدهما لا يحمل وحدة الآخر
  (`java.desktop` للأول و JavaFX للثاني).

## الاستخدام

`pom.xml`:

```xml
<repositories>
    <!-- قبل JitPack دائمًا: انظر الملاحظة أسفل المقطع -->
    <repository>
        <id>central</id>
        <url>https://repo.maven.apache.org/maven2</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.MohamedSalah-13</groupId>
    <artifactId>app-icons</artifactId>
    <version>v0.1.0</version>
</dependency>
```

**لا تحذف `central` من المقطع.** مستودعات المشروع تُسأل قبل Maven Central الموروث، فلو ذُكر `jitpack.io` وحده
لسأله Maven أولًا عن كل مكتبة في المشروع. و JitPack يردّ على بعض ما ليس عنده بملف فارغ بدل 404 (مثل
`xml-apis:xml-apis-ext`)، فيُحفظ جار فارغ ويفشل البناء بـ `zip file is empty` على أي جهاز مستودعه المحلي فارغ،
وأولها خادم CI. يعمل على جهازك لأن الجار السليم محفوظ عندك من قبل.

مع `module-info.java` أضف: `requires com.hamza.icons;`

### 1. الرسم وكتابة الملفات

ملف واحد في المشروع، مثلًا `packaging/AppIcon.java`، فيه الرسم فقط:

```java
import com.hamza.icons.IconDrawing;
import com.hamza.icons.IconSet;

public class AppIcon {
    public static void main(String[] args) throws Exception {
        IconSet.of(IconDrawing.grid(256, AppIcon::draw))
                .ico("packaging/app.ico")                       // مشغّل Windows والمثبّت
                .icns("packaging/app.icns")                     // تطبيق macOS
                .png("packaging/app.png", 512)                  // حزمة Linux
                .pngs("src/main/resources/img/icon-%d.png")     // نوافذ البرنامج
                .write(java.nio.file.Path.of(args.length > 0 ? args[0] : "."))
                .forEach(file -> System.out.println("wrote " + file));
    }

    /** الرسم على شبكة 256؛ size هو المقاس الفعلي بالبكسل، وبه يُقرَّر ما يُحذف من التفاصيل. */
    static void draw(java.awt.Graphics2D g, int size) {
        boolean tiny = size <= 24;
        g.setColor(new java.awt.Color(0x1D4ED8));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(8, 8, 240, 240, 64, 64));
        if (!tiny) {
            // التفاصيل التي تتحول إلى ضبابية في المقاسات الصغيرة
        }
    }
}
```

يُشغَّل يدويًا عند تعديل الأيقونة فقط، من جذر المشروع، والملفات الناتجة تُحفظ في المستودع:

```
java -cp "%USERPROFILE%\.m2\repository\com\github\MohamedSalah-13\app-icons\v0.1.0\app-icons-v0.1.0.jar" packaging\AppIcon.java .
```

الجار موجود في هذا المسار بعد أي بناء Maven للمشروع.

- `IconDrawing.grid(256, ...)` للرسم بإحداثيات ثابتة أيًّا كان المقاس. بدونها يستلم الرسم إحداثيات
  البكسل مباشرة، وهذا ما يحتاجه رسم يريد خطوطًا على حدود البكسل في المقاسات الصغيرة.
- لبرنامج له أكثر من أيقونة (عميل وخادم مثلًا): `IconSet` لكل رسم.
- `ico(path, 16, 32, 48, 256)` لاختيار المقاسات، و `pngs(pattern, 16, 32)` كذلك.

### 2. أيقونة النافذة

```java
private static final AppIcons ICONS = AppIcons.load(Main.class::getResourceAsStream, "/img/icon-%d.png");

ICONS.applyTo(stage);                 // النافذة الرئيسية
ICONS.applyTo(dialog.getDialogPane().getScene().getWindow());   // الحوار لا يرث أيقونة مالكه
ICONS.largest();                      // أكبر صورة، لنافذة «حول البرنامج»
```

`Main.class::getResourceAsStream` تُكتب في البرنامج نفسه، فتقرأ موارده حتى من وحدة (module) لا تفتح
حزمة الصور. المقاس الذي لا يوجد ملفه يُسجَّل تحذيرًا ويُتجاوز، فلا يتعطل تشغيل البرنامج بسبب أيقونة.

## ما الذي يُكتب

| الملف | المقاسات | الصيغة |
|---|---|---|
| `.ico` | 16، 20، 24، 32، 40، 48، 64، 96، 128، 256 | 256 بصيغة PNG، والباقي Bitmap ‏32 بت |
| `.icns` | 32، 64، 128، 256، 512، 1024 | PNG تحت الأنواع `ic11` `ic12` `ic07` `ic08` `ic09` `ic10` |
| `pngs` | 16، 24، 32، 48، 64، 128، 256 | PNG شفاف |

- مقاسات `.ico` هي ما يطلبه Explorer وشريط المهام والاختصار من تكبير 100% إلى 250%.
- المقاسات الأصغر من 256 تُخزَّن Bitmap لأن كل أداة تتعامل مع الأيقونات تقرؤها (Inno Setup ومترجم
  الموارد منها)، بينما PNG داخل `.ico` لا يضمنه إلا Windows نفسه. لذلك الملف أكبر: نحو 150 كيلوبايت.
- لمن يحتاج تحكمًا أدق: `Ico.encode(frames, pngFrom)` و `Icns.encode(drawing)` يعيدان البايتات مباشرة.

## ما ليس فيها بعد

أيقونات Android ‏(mipmap والأيقونة المتكيفة) وأيقونات PWA ‏(maskable و apple-touch) تُكتب اليوم بـ
`png(path, size)` لكل ملف. لا يوجد لها اختصار، ولا قراءة للرسم من ملف SVG.
