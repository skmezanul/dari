package com.psddev.dari.test;

import com.psddev.dari.db.Location;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Sorter;
import com.psddev.dari.util.TypeDefinition;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class SearchArticleIndexTestH2 extends SearchArticleIndexTest {

    @Override
    @Test
    public void sortAscendingReferenceOneOne() {
        super.sortAscendingReferenceOneOne();
    }

}
