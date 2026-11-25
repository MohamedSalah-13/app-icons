# app-icons

مكتبة Java صغيرة تكتب ملفات أيقونة البرنامج كلها **من رسم واحد**، وتحمّلها على نوافذ JavaFX.
كل برنامج يحتفظ برسمه فقط؛ كتابة `.ico` و `.icns` وصور PNG تُكتب مرة واحدة هنا بدل نسخها في كل مشروع.

- **وقت تعديل الأيقونة** (`com.hamza.icons`): ترسم كل مقاس مستقلًا ثم تكتب ملف Windows ‏(`.ico`)،
  وملف macOS ‏(`.icns`)، وصورة Linux، وصور نوافذ البرنامج.
- **وقت التشغيل** (`com.hamza.icons.fx`): تضع الأيقونة بكل مقاساتها على النافذة والحوارات.
- **من SVG** (`com.hamza.icons.svg`): لمن أيقونته ملف SVG لا رسم بالكود، كتطبيقات الويب؛ بلا متصفح.
- تحتاج المكتبة Java 21 فأحدث ووحدة `java.desktop` من الـJDK. تحميل أيقونات النوافذ يحتاج JavaFX،
  وقراءة SVG تحتاج JSVG.

## تحويل SVG من سطر الأوامر

نزّل [ملف CLI للإصدار v0.7.0](https://github.com/MohamedSalah-13/app-icons/releases/download/v0.7.0/app-icons-0.7.0-cli.jar)، ثم شغّله باستخدام Java 21 فأحدث:

```shell
java -jar app-icons-0.7.0-cli.jar logo.svg output
```

للبناء من المصدر، شغّل `mvn verify` من جذر المشروع. ستجد الملف في `target/app-icons-0.7.0-cli.jar`.

يحمل ملف الـCLI مكتبة JSVG معه. ينشئ الأمر داخل `output` ملفّي `app.ico` و`app.icns`،
وصورة `app.png` بمقاس 512، وسبع صور داخل `png/` بأسماء مثل `icon-16.png` و`icon-256.png`.
يطبع مسار كل ملف بعد نجاح الكتابة. يعرض الخيار `--help` طريقة الاستخدام.

### أيقونات PWA

بعد تنزيل ملف الـCLI، شغّل الأمر التالي لتوليد أيقونات الويب وملف الـmanifest في مجلد النشر.

```shell
java -jar app-icons-0.7.0-cli.jar --pwa "My App.svg" public
```

| المخرجات داخل `public` | الاستخدام |
|---|---|
| `icon-192.png` و`icon-512.png` | أيقونتان بالشفافية الأصلية |
| `maskable-192.png` و`maskable-512.png` | خلفية بيضاء معتمة والشعار بالكامل داخل دائرة القص الآمنة |
| `apple-touch-icon.png` | أيقونة شاشة iOS الرئيسية بمقاس 180×180 وخلفية بيضاء |
| `manifest.webmanifest` | اسم التطبيق من اسم ملف SVG، ووضع العرض `standalone`، ومسارات نسبية للأيقونات |

ضع الملفات بجوار صفحة بدء التطبيق. يشير `start_url` و`scope` إلى مجلد الـmanifest نفسه (`./`)،
فيدعم النشر تحت مسار فرعي. أضف الوسمين التاليين داخل `<head>` في صفحة البدء:

```html
<link rel="manifest" href="./manifest.webmanifest">
<link rel="apple-touch-icon" sizes="180x180" href="./apple-touch-icon.png">
```

### تخصيص قالب PWA وخلفية الأيقونات

في النسخة المبنية من المصدر، يقبل `--pwa` خيارين مستقلين. يقرأ `--manifest-template` ملف JSON بترميز UTF-8.
مثال لملف `config/app.webmanifest`:

```json
{
  "name": "صندوق البريد",
  "short_name": "البريد",
  "theme_color": "#17324d",
  "shortcuts": [{"name": "الوارد", "url": "./inbox"}]
}
```

```shell
mvn verify
java -jar target/app-icons-0.7.0-cli.jar --pwa --manifest-template config/app.webmanifest --background-color "#17324d" logo.svg public
java -jar target/app-icons-0.7.0-cli.jar --check --pwa --manifest-template config/app.webmanifest --background-color "#17324d" logo.svg public
```

احتفظ بالقالب كملف مصدر مستقل عن المخرجات. يستبدل المولّد مصفوفة `icons` في المستوى الأعلى، ويحافظ على بقية
حقول القالب، بما فيها أيقونات `shortcuts`، ويضيف القيم الافتراضية للحقول الناقصة. تُفسَّر الروابط النسبية
في المتصفح نسبةً إلى مكان نشر `manifest.webmanifest`.

يقبل `--background-color` لونًا معتمًا بصيغة `#RRGGBB` لخلفية أيقونات maskable وApple touch.
اللون الافتراضي أبيض، وتحتفظ الأيقونات العادية بالشفافية الأصلية. إذا حدّد القالب `background_color`
تُحفظ قيمته في الـmanifest، وإلا يكتب المولّد لون خلفية الأيقونات في هذا الحقل.

### فحص الملفات في CI

استخدم ملف الـCLI المنزّل لفحص المخرجات المحفوظة في مشروعك.

```shell
# أيقونات سطح المكتب
java -jar app-icons-0.7.0-cli.jar --check logo.svg output

# أيقونات الويب وملف manifest.webmanifest
java -jar app-icons-0.7.0-cli.jar --check --pwa "My App.svg" public
```

يقارن `--check` محتوى كل ملف متوقع بايتًا ببايت، ويتجاهل الملفات الإضافية. يقرأ الملفات الموجودة
ويترك محتواها ومواعيد تعديلها كما هي، حتى لو كان مجلد الإخراج نفسه مفقودًا.
كود الخروج `0` يعني التطابق. عند وجود فروق يطبع `MISSING` أو `DIFFERENT` مع مسار كل ملف ويخرج
بالكود `1`. أخطاء القراءة أو الرسم تفشل أيضًا بالكود `1`، وصيغة الأمر غير الصحيحة ترجع `2`.

في المستودع الذي تحفظ فيه ملفات التطبيق، أضف هذا السطر إلى `.gitattributes` حتى يظل
الـmanifest بنهايات أسطر LF بعد Git checkout على Windows وLinux، بما في ذلك عند تفعيل `core.autocrlf=true`.

```gitattributes
*.webmanifest text eol=lf
```

لو كان الملف متتبّعًا قبل إضافة القاعدة، شغّل `git add --renormalize -- public/manifest.webmanifest`
بمسار الملف في مشروعك، ثم احفظ التغيير مع `.gitattributes` في نفس الـcommit.

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
    <version>v0.7.0</version>
</dependency>
```

**لا تحذف `central` من المقطع.** مستودعات المشروع تُسأل قبل Maven Central الموروث، فلو ذُكر `jitpack.io` وحده
لسأله Maven أولًا عن كل مكتبة في المشروع. و JitPack يردّ على بعض ما ليس عنده بملف فارغ بدل 404 (مثل
`xml-apis:xml-apis-ext`)، فيُحفظ جار فارغ ويفشل البناء بـ `zip file is empty` على أي جهاز مستودعه المحلي فارغ،
وأولها خادم CI. يعمل على جهازك لأن الجار السليم محفوظ عندك من قبل.

عند استخدام Java Modules، أضف إلى `module-info.java` السطر `requires com.hamza.icons;`.
إذا استعملت `SvgDrawing` فأضف أيضًا `requires com.github.weisj.jsvg;` وضع JSVG على
الـmodule path وقت الترجمة والتشغيل. وإذا استعملت `AppIcons` فأضف `requires javafx.graphics;`
أو وحدة JavaFX تتيحها لتطبيقك مثل `javafx.controls`.

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
java -cp "%USERPROFILE%\.m2\repository\com\github\MohamedSalah-13\app-icons\v0.7.0\app-icons-v0.7.0.jar" packaging\AppIcon.java .
```

الجار موجود في هذا المسار بعد أي بناء Maven للمشروع.

- `IconDrawing.grid(256, ...)` للرسم بإحداثيات ثابتة أيًّا كان المقاس. بدونها يستلم الرسم إحداثيات
  البكسل مباشرة، وهذا ما يحتاجه رسم يريد خطوطًا على حدود البكسل في المقاسات الصغيرة.
- لبرنامج له أكثر من أيقونة (عميل وخادم مثلًا): `IconSet` لكل رسم.
- `ico(path, 16, 32, 48, 256)` لاختيار المقاسات، و `pngs(pattern, 16, 32)` كذلك.
- مسارات الملفات نسبية إلى مجلد `write(root)`. يجهّز البرنامج كل الملفات أولًا، ثم يستبدل كل ملف
  بإعادة تسمية ذرّية على نظام الملفات. إذا فشل استبدال ملف، يحاول استرجاع الملفات السابقة وحذف
  الملفات الجديدة. ينطبق الاسترجاع على الأخطاء أثناء التشغيل؛ انقطاع الكهرباء أو إنهاء العملية
  أثناء الاستبدال قد يترك بعض الملفات محدّثة.

### 2. أيقونة النافذة

```java
private static final AppIcons ICONS = AppIcons.load(Main.class::getResourceAsStream, "/img/icon-%d.png");

ICONS.applyTo(stage);                 // النافذة الرئيسية
ICONS.applyTo(dialog.getDialogPane().getScene().getWindow());   // الحوار لا يرث أيقونة مالكه
ICONS.largest();                      // أكبر صورة، لنافذة «حول البرنامج»
```

`Main.class::getResourceAsStream` تُكتب في البرنامج نفسه، فتقرأ موارده حتى من وحدة (module) لا تفتح
حزمة الصور. المقاس الذي لا يوجد ملفه يُسجَّل تحذيرًا ويُتجاوز، فلا يتعطل تشغيل البرنامج بسبب أيقونة.

### 3. أيقونة تطبيق Android

من الرسم نفسه، داخل مجلد `res` للتطبيق (تطبيق Flutter مثلًا):

```java
String res = "android/app/src/main/res";
IconSet.of(icon)                                      // الأيقونة كاملة: العلامة على خلفيتها
        .androidLauncher(res)                         // ما قبل Android 8
        .androidAdaptive(res, mark, TOP, BOTTOM)      // Android 8 فأحدث
        .write(appRoot);
```

- `androidLauncher` يكتب الأيقونة كاملة بمقاس 48dp في الكثافات الخمس: `mipmap-<density>/ic_launcher.png`.
- `androidAdaptive` يكتب الأيقونة المتكيفة التي يقصّها المشغّل بشكله (دائرة أو مربع مستدير):
  العلامة وحدها `mipmap-<density>/ic_launcher_foreground.png`، والخلفية `drawable/ic_launcher_background.xml`
  (لون واحد، أو لونان من الأعلى إلى الأسفل)، والملف الذي يجمعهما `mipmap-anydpi-v26/ic_launcher.xml`.
- `mark` رسم ثانٍ: **العلامة بلا خلفية** على مربع شفاف 108dp. المشغّل يعرض الوسط 72 وقد يقصّه إلى دائرة قطرها 66،
  فتبقى العلامة داخل الـ 66 الوسطى. لو رُسمت الخلفية داخل الصورة لظهرت زواياها داخل القصّ.
- العلامة نفسها تُعطى أيضًا كطبقة monochrome، وهي التي يلوّنها المشغّل ذو السمات بحسب شكلها فقط.
- الاسم دائمًا `ic_launcher`، وهو ما يتوقعه Android و Flutter.

### 4. الرسم من ملف SVG

لتطبيق ويب أيقونته ملف SVG: الملف نفسه هو الرسم، وكل مقاس يُرسم منه مباشرة، بلا Playwright ولا متصفح.

```java
IconSet.of(SvgDrawing.of(Path.of("src/main/resources/static/icons/app.svg")))
        .png("src/main/resources/static/icons/app-192.png", 192)
        .png("src/main/resources/static/icons/app-512.png", 512)
        .ico("src/main/resources/static/favicon.ico", 16, 32, 48)
        .write(projectRoot);
```

- `SvgDrawing.of(String)` يأخذ نص SVG مباشرة، وهو المناسب حين تختلف الصيغ: أيقونة بزوايا مستديرة، وأخرى
  maskable تملأ المربع كله، وثالثة لـ iOS. كل صيغة نص SVG و `IconSet` خاص بها.
- يُفضَّل أن يحمل الملف `viewBox` مربعًا؛ ما يرسمه داخله يملأ الأيقونة في كل مقاس.
- تُقرأ الصور المشار إليها بمسار نسبي في `<image href="images/mark.png">`
  أو `xlink:href` من مجلد ملف SVG. احتفظ بالصور مع الرسم في Git حتى يجدها CI. إذا غابت صورة،
  يتوقف التوليد و`--check` برسالة تتضمن اسمها. ينطبق هذا على الـCLI و`SvgDrawing.of(Path)`.
- **هذا الجزء وحده يحتاج مكتبة أخرى**، تُضاف في مشروعك بجوار app-icons:

```xml
<dependency>
    <groupId>com.github.weisj</groupId>
    <artifactId>jsvg</artifactId>
    <version>2.2.0</version>
</dependency>
```

  ومع التشغيل بـ `java -cp` يُضاف جارها إلى المسار بجوار جار app-icons. من لا يستخدم `SvgDrawing` لا يحتاجها.
- الناتج لا يطابق لقطة المتصفح بايت ببايت: محرّك الرسم مختلف، فتختلف بكسلات التنعيم على الحواف. في أيقونتين
  حقيقيتين قورنتا بلقطات Chromium كان متوسط الفرق أقل من درجة لون واحدة من 255 في المقاسات 180 فأكبر، ونحو
  ثلاث درجات في مقاس 32 حيث الحواف أغلب الصورة.

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
