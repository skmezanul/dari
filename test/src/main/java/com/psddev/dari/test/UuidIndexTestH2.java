package com.psddev.dari.test;

import com.psddev.dari.db.Location;
import com.psddev.dari.db.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class UuidIndexTestH2 extends UuidIndexTest {

    @Override
    @Test
    public void sortAscendingReferenceOneOne() {
        super.sortAscendingReferenceOneOne();
    }

}
