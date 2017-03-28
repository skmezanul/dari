package com.psddev.dari.test;

import com.psddev.dari.db.Database;
import com.psddev.dari.db.Grouping;
import com.psddev.dari.db.Query;
import com.psddev.dari.sql.AbstractSqlDatabase;
import com.psddev.dari.util.PaginatedResult;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class ReadTestH2 extends ReadTest {

    @Override
    @Test
    public void iterableById0() {
        super.iterable(false, 0, true);
    }

    @Override
    @Test
    public void iterableById1() {
        super.iterable(false, 1, true);
    }

    @Override
    @Test
    public void iterableNotById0() {
        super.iterable(true, 0, true);
    }

    @Override
    @Test
    public void iterableNotById1() {
        super.iterable(true, 1, true);
    }

    @Override
    @Test(expected = NoSuchElementException.class)
    public void iterableNextById() {
        super.iterableNext(false, true);
    }

    @Override
    @Test(expected = NoSuchElementException.class)
    public void iterableNextNotById() {
        super.iterableNext(true, true);
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void iterableRemoveById() {
        super.iterableRemove(false, true);
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void iterableRemoveNotById() {
        super.iterableRemove(true, true);
    }

}
