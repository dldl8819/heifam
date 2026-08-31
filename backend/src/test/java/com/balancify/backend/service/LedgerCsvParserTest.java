package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerCsvParserTest {

    @Test
    void skipsHeaderRowAndParsesSimpleRows() {
        String csv = "date,amount,category,memo\n2026-01-05,10000,YOUR_CATEGORY,YOUR_MEMO\n2026-01-06,20000,OTHER,\n";

        List<List<String>> rows = LedgerCsvParser.parseDataRows(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("2026-01-05", "10000", "YOUR_CATEGORY", "YOUR_MEMO");
        assertThat(rows.get(1)).containsExactly("2026-01-06", "20000", "OTHER", "");
    }

    @Test
    void handlesQuotedFieldsWithEmbeddedCommasAndQuotes() {
        String csv = "date,amount,category,memo\n"
            + "2026-01-05,10000,\"YOUR, CATEGORY\",\"He said \"\"hi\"\"\"\n";

        List<List<String>> rows = LedgerCsvParser.parseDataRows(csv);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly("2026-01-05", "10000", "YOUR, CATEGORY", "He said \"hi\"");
    }

    @Test
    void handlesEmbeddedNewlinesInsideQuotedFields() {
        String csv = "date,amount,category,memo\n"
            + "2026-01-05,10000,YOUR_CATEGORY,\"line one\nline two\"\n"
            + "2026-01-06,5000,OTHER,plain\n";

        List<List<String>> rows = LedgerCsvParser.parseDataRows(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get(3)).isEqualTo("line one\nline two");
        assertThat(rows.get(1).get(3)).isEqualTo("plain");
    }

    @Test
    void returnsEmptyListForBlankContent() {
        assertThat(LedgerCsvParser.parseDataRows(null)).isEmpty();
        assertThat(LedgerCsvParser.parseDataRows("")).isEmpty();
        assertThat(LedgerCsvParser.parseDataRows("date,amount,category,memo\n")).isEmpty();
    }

    @Test
    void cellReturnsEmptyStringForMissingIndex() {
        List<String> row = List.of("a", "b");

        assertThat(LedgerCsvParser.cell(row, 0)).isEqualTo("a");
        assertThat(LedgerCsvParser.cell(row, 5)).isEqualTo("");
        assertThat(LedgerCsvParser.cell(null, 0)).isEqualTo("");
    }
}
