package com.school.student.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

    @Test
    void splitsPlainRowsAndTrimsFields() {
        List<List<String>> records = CsvParser.parse("a,b,c\n1, 2 ,3\n");

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsExactly("a", "b", "c");
        assertThat(records.get(1)).containsExactly("1", "2", "3");
    }

    @Test
    void keepsCommasAndQuotesInsideQuotedFields() {
        List<List<String>> records = CsvParser.parse("""
                name,note
                "Sharma, Aarav","He said ""hello"" twice"
                """);

        assertThat(records).hasSize(2);
        assertThat(records.get(1)).containsExactly("Sharma, Aarav", "He said \"hello\" twice");
    }

    @Test
    void handlesCarriageReturnsBomAndBlankLines() {
        List<List<String>> records = CsvParser.parse("\uFEFFa,b\r\n1,2\r\n\r\n3,4");

        assertThat(records).hasSize(3);
        assertThat(records.get(0)).containsExactly("a", "b");
        assertThat(records.get(2)).containsExactly("3", "4");
    }

    @Test
    void keepsNewlinesInsideQuotedFields() {
        List<List<String>> records = CsvParser.parse("a,b\n\"line one\nline two\",2");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).get(0)).isEqualTo("line one\nline two");
    }

    @Test
    void returnsNothingForEmptyInput() {
        assertThat(CsvParser.parse("")).isEmpty();
        assertThat(CsvParser.parse("   \n  \n")).isEmpty();
    }
}
