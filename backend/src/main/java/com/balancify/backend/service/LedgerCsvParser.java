package com.balancify.backend.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC4180-style CSV parser: handles quoted fields containing commas, embedded
 * double-quotes ("") and newlines. The first non-empty row is treated as a header and skipped.
 */
final class LedgerCsvParser {

    private LedgerCsvParser() {
    }

    static List<List<String>> parseDataRows(String content) {
        List<List<String>> allRows = parseRows(content);
        if (allRows.isEmpty()) {
            return List.of();
        }
        return allRows.subList(1, allRows.size());
    }

    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean insideQuotes = false;
        boolean rowHasContent = false;
        int index = 0;
        int length = content.length();

        while (index < length) {
            char current = content.charAt(index);

            if (insideQuotes) {
                if (current == '"') {
                    if (index + 1 < length && content.charAt(index + 1) == '"') {
                        currentField.append('"');
                        index += 2;
                        continue;
                    }
                    insideQuotes = false;
                    index++;
                    continue;
                }
                currentField.append(current);
                index++;
                continue;
            }

            if (current == '"') {
                insideQuotes = true;
                rowHasContent = true;
                index++;
                continue;
            }
            if (current == ',') {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                rowHasContent = true;
                index++;
                continue;
            }
            if (current == '\r') {
                index++;
                continue;
            }
            if (current == '\n') {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                if (rowHasContent || currentRow.size() > 1) {
                    rows.add(currentRow);
                }
                currentRow = new ArrayList<>();
                rowHasContent = false;
                index++;
                continue;
            }

            currentField.append(current);
            rowHasContent = true;
            index++;
        }

        currentRow.add(currentField.toString());
        if (rowHasContent || currentRow.size() > 1) {
            rows.add(currentRow);
        }

        return rows;
    }

    static String cell(List<String> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }
}
