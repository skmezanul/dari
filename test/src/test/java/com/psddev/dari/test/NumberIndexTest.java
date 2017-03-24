package com.psddev.dari.test;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category({com.psddev.dari.test.ElasticTest.class, com.psddev.dari.test.H2Test.class})
public class NumberIndexTest extends AbstractIndexTest<NumberIndexModel, Double> {

    @Override
    protected Class<NumberIndexModel> modelClass() {
        return NumberIndexModel.class;
    }

    @Override
    protected Double value(int index) {
        return (double) index;
    }

    @Override
    @Test
    public void sortDescendingOne() {
        super.sortDescendingOne();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortClosestEmbeddedOneOne() {
        super.sortClosestEmbeddedOneOne();
    }

    @Override
    @Test
    public void sortAscendingOne() {
        super.sortAscendingOne();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void contains() {
        super.contains();
    }

    @Category({ com.psddev.dari.test.ElasticExcludeTest.class })
    @Override
    @Test
    public void sortAscendingReferenceOneOne() {
        super.sortAscendingReferenceOneOne();
    }

    @Override
    @Test
    public void sortAscendingEmbeddedOneOne() {
        super.sortAscendingEmbeddedOneOne();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortClosestReferenceOneOne() {
        super.sortClosestReferenceOneOne();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void sortFarthestOneAnd() {
        super.sortFarthestOneAnd();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void containsNull() {
        createCompareTestModels();
        query().and("one contains ?", (Object) null).count();
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void startsWithNull() {
        createCompareTestModels();
        query().and("one startsWith ?", (Object) null).count();
    }

    @Override
    @Test
    public void invalidValue() {
    }
}
