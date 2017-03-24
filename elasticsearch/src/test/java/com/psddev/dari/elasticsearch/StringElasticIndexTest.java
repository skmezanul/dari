package com.psddev.dari.elasticsearch;

import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@org.junit.Ignore
public class StringElasticIndexTest extends AbstractElasticIndexTest<StringElasticIndexModel, String> {

    @Override
    protected Class<StringElasticIndexModel> modelClass() {
        return StringElasticIndexModel.class;
    }

    @Override
    protected String value(int index) {
        return String.valueOf((char) ('a' + index));
    }

    @Override
    @Test
    public void invalidValue() {
    }

    @Test
    public void blankValue() {
        StringElasticIndexModel model = model().one(" ").create();
        assertThat(
                query().where("one = missing").first(),
                is(model));
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortClosestEmbeddedOneOne() {
        super.sortClosestEmbeddedOneOne();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortFarthestOneAnd() {
        super.sortFarthestOneAnd();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortClosestReferenceOneOne() {
        super.sortClosestReferenceOneOne();
    }

    @Override
    @Test
    public void sortDescendingOne() {
        super.sortDescendingOne();
    }

    @Override
    @Test
    public void sortAscendingOne() {
        super.sortAscendingOne();
    }

    @Override
    @Test
    public void sortAscendingEmbeddedOneOne() {
        super.sortAscendingEmbeddedOneOne();
    }

    @Override
    @Test
    public void contains() {
        StringElasticIndexModel model = model().one("abcde").create();
        assertThat(
                query().where("one contains ?", "bcd").first(),
                is(model));
    }

    @Override
    @Test
    public void startsWith() {
        StringElasticIndexModel model = model().one("abcde").create();
        assertThat(
                query().where("one startsWith ?", "abc").first(),
                is(model));
    }
}
