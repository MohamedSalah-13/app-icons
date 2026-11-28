package com.hamza.icons.cli;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks file resources and values aliases in the resource configurations the CLI writes. */
final class AndroidResourceConflicts {
    // Only configurations used by generated resources can conflict. Other axes (e.g. night) stay distinct.
    private static final Pattern DENSITY_VERSION = Pattern.compile(
            "(?:-(mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|anydpi|0*[1-9][0-9]*dpi))?(?:-v0*([0-9]{1,5}))?");

    private AndroidResourceConflicts() {}

    static void check(Path res, List<Path> outputs) throws IOException {
        if (!Files.isDirectory(res)) {
            return;
        }
        Map<String, Map<String, Path>> targetsByConfig = new HashMap<>();
        for (Path output : outputs) {
            Path dir = output.getParent();
            if (dir.equals(res)) {
                continue; // Web and preview files may also have been requested.
            }
            String directory = dir.getFileName().toString();
            int dash = directory.indexOf('-');
            String type = dash < 0 ? directory : directory.substring(0, dash);
            String config = configuration(directory.substring(type.length()));
            targetsByConfig.computeIfAbsent(config, ignored -> new HashMap<>()).put(type + "/" + resourceName(output), output);
        }
        try (var directories = Files.newDirectoryStream(res)) {
            for (Path dir : directories) {
                String directory = dir.getFileName().toString();
                int dash = directory.indexOf('-');
                String type = dash < 0 ? directory : directory.substring(0, dash);
                if (!type.equals("values") && !type.equals("mipmap") && !type.equals("drawable")) {
                    continue;
                }
                Map<String, Path> targets = targetsByConfig.get(configuration(directory.substring(type.length())));
                if (targets != null && Files.isDirectory(dir)) {
                    checkDirectory(dir, type, targets);
                }
            }
        }
    }

    private static void checkDirectory(Path dir, String type, Map<String, Path> targets) throws IOException {
        boolean values = type.equals("values");
        try (var files = Files.newDirectoryStream(dir, values ? "*.xml" : "*.*")) {
            for (Path file : files) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (values) {
                    checkValues(file, targets);
                } else {
                    String key = type + "/" + resourceName(file);
                    Path target = targets.get(key);
                    if (target != null && !file.equals(target)) {
                        throw conflict(key, file, target);
                    }
                }
            }
        }
    }

    private static String configuration(String qualifiers) {
        var match = DENSITY_VERSION.matcher(qualifiers.toLowerCase(Locale.ROOT));
        if (!match.matches()) {
            return qualifiers;
        }
        String density = switch (match.group(1)) {
            case null -> "";
            case "mdpi" -> "160dpi";
            case "hdpi" -> "240dpi";
            case "xhdpi" -> "320dpi";
            case "xxhdpi" -> "480dpi";
            case "xxxhdpi" -> "640dpi";
            case "anydpi" -> "65534dpi";
            default -> match.group(1).replaceFirst("^0+", "");
        };
        // AAPT2 raises lower SDK qualifiers to API 4 for densities and API 21 for anydpi.
        int minimum = switch (density) {
            case "" -> 0;
            case "65534dpi" -> 21;
            default -> 4;
        };
        int version = match.group(2) == null ? 0 : Integer.parseInt(match.group(2));
        return density + "-v" + Math.max(minimum, version);
    }

    private static String resourceName(Path path) {
        String file = path.getFileName().toString();
        return file.endsWith(".9.png") ? file.substring(0, file.length() - 6) : file.substring(0, file.lastIndexOf('.'));
    }

    private static void checkValues(Path file, Map<String, Path> targets) throws IOException {
        XMLInputFactory xml = XMLInputFactory.newDefaultFactory();
        xml.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xml.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (var in = Files.newInputStream(file)) {
            var reader = xml.createXMLStreamReader(in);
            try {
                int depth = 0;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.END_ELEMENT) {
                        depth--;
                    } else if (event == XMLStreamConstants.START_ELEMENT) {
                        depth++;
                        if (depth != 2 || reader.getNamespaceURI() != null) {
                            continue;
                        }
                        String type = reader.getLocalName();
                        if (type.equals("item")) {
                            type = reader.getAttributeValue(null, "type");
                        }
                        String key = type + "/" + reader.getAttributeValue(null, "name");
                        Path target = targets.get(key);
                        if (target != null) {
                            throw conflict(key, file, target);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Cannot read Android values resource " + file + ": " + e.getMessage(), e);
        }
    }

    private static IOException conflict(String key, Path existing, Path target) {
        return new IOException("Android resource conflict @" + key + ": " + existing + " conflicts with " + target);
    }
}
