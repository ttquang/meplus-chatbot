package com.ttq.process;

import com.ttq.process.FieldOptions.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TagsFieldTest {

    private static final List<String> ALLOWED = List.of("TAG-1M", "TAG-2M", "TAG-DUC", "TAG-TRONG");

    private final FieldDefinition tags = new FieldDefinition("productTags", FieldType.TAGS, null, null,
            "productTagOptions");

    @Test
    void keepsTheAllowedTagsInTheirListedSpelling() {
        assertThat(tags.normalize(List.of("tag-1m", "TAG-DUC", "TAG-NONE", "TAG-1M"), ALLOWED))
                .contains(List.of("TAG-1M", "TAG-DUC"));
        assertThat(tags.normalize("TAG-2M, tag-trong", ALLOWED)).contains(List.of("TAG-2M", "TAG-TRONG"));
    }

    @Test
    void anEmptyListClearsTheTagsButAListOfMistakesIsRejected() {
        assertThat(tags.normalize(List.of(), ALLOWED)).contains(List.of());
        assertThat(tags.normalize("", ALLOWED)).contains(List.of());
        // So a mistake does not wipe out what was collected.
        assertThat(tags.normalize(List.of("TAG-NONE"), ALLOWED)).isEmpty();
        assertThat(tags.normalize(null, ALLOWED)).isEmpty();
    }

    @Test
    void aConditionHoldsWhenTheTagIsAmongThoseCollected() {
        assertThat(tags.valuesMatch("tag-duc", List.of("TAG-1M", "TAG-DUC"))).isTrue();
        assertThat(tags.valuesMatch("TAG-TRONG", List.of("TAG-1M", "TAG-DUC"))).isFalse();
        assertThat(tags.hasDynamicValues()).isTrue();
    }

    @Test
    void theNextGroupIsTheFirstUnansweredOneWithAChoiceLeft() {
        FieldOptions options = new FieldOptions(List.of(
                tag("TAG-TUI", "Loại sản phẩm", 3),
                tag("TAG-DE", "Loại sản phẩm", 0),
                tag("TAG-1M", "Hệ", 3),
                tag("TAG-2M", "Hệ", 2),
                tag("TAG-DUC", "Độ trong", 1),
                tag("TAG-TRONG", "Độ trong", 1)), true);

        // Loại sản phẩm has one answer left, so asking it would not narrow anything.
        assertThat(options.nextGroup(List.of())).extracting(Option::value).containsExactly("TAG-1M", "TAG-2M");
        assertThat(options.nextGroup(List.of("tag-1m"))).extracting(Option::value)
                .containsExactly("TAG-DUC", "TAG-TRONG");
        assertThat(options.nextGroup(List.of("TAG-1M", "TAG-DUC"))).isEmpty();
        assertThat(FieldOptions.fixed(List.of("A", "B")).nextGroup(List.of())).isEmpty();
    }

    private static Option tag(String code, String group, long count) {
        return new Option(code, code, group, count, null);
    }
}
