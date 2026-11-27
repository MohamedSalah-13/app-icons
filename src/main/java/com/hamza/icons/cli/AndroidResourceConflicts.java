package com.hamza.icons.cli;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checks file resources and values aliases in the resource configurations the CLI writes. */
final class AndroidResourceConflicts {
    private AndroidResourceConflicts() {}

    static void check(Path res, List<Path> outputs) throws IOException {
        Map<Path, Map<String, Path>> aliases = new HashMap<>();
        for (Path output : outputs) {
            Path dir = output.getParent();
            if (dir.equals(res)) {
                continue; // Web and preview files may also have been requested.
            }
            String directory = dir.getFileName().toString();
            int dash = directory.indexOf('-');
            String type = dash < 0 ? directory : directory.substring(0, dash);
            String qualifier = directory.substring(type.length());
            String name = resourceName(output);
            String key = type + "/" + name;
            aliases.computeIfAbsent(res.resolve("values" + qualifier), ignored -> new HashMap<>()).put(key, output);
            if (Files.isDirectory(dir)) {
                try (var siblings = Files.newDirectoryStream(dir, name + ".*")) {
                    for (Path file : siblings) {
                        if (!file.equals(output) && resourceName(file).equals(name) && Files.isRegularFile(file)) {
                            throw conflict(key, file, output);
                        }
                    }
                }
            }
        }
        for (var entry : aliases.entrySet()) {
            if (Files.isDirectory(entry.getKey())) {
                try (var files = Files.newDirectoryStream(entry.getKey(), "*.xml")) {
                    for (Path file : files) {
                        if (Files.isRegularFile(file)) {
                            checkValues(file, entry.getValue());
                        }
                    }
                }
            }
        }
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
