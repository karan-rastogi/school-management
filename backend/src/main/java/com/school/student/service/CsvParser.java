package com.school.student.service;

import java.util.ArrayList;
import java.util.List;

public final class CsvParser {

    private static final char BOM = '\uFEFF';

    private CsvParser() {
    }

    public static List<List<String>> parse(String source) {
        List<List<String>> records = new ArrayList<>();
        if (source == null) {
            return records;
        }

        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int index = source.startsWith(String.valueOf(BOM)) ? 1 : 0;

        for (; index < source.length(); index++) {
            char character = source.charAt(index);

            if (quoted) {
                if (character == '"') {
                    boolean escaped = index + 1 < source.length() && source.charAt(index + 1) == '"';
                    if (escaped) {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            switch (character) {
                case '"' -> quoted = true;
                case ',' -> {
                    record.add(field.toString().trim());
                    field.setLength(0);
                }
                case '\r' -> {
                }
                case '\n' -> {
                    record.add(field.toString().trim());
                    field.setLength(0);
                    addIfNotBlank(records, record);
                    record = new ArrayList<>();
                }
                default -> field.append(character);
            }
        }

        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString().trim());
            addIfNotBlank(records, record);
        }

        return records;
    }

    private static void addIfNotBlank(List<List<String>> records, List<String> record) {
        boolean hasContent = record.stream().anyMatch(field -> !field.isBlank());
        if (hasContent) {
            records.add(List.copyOf(record));
        }
    }
}
