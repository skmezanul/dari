package com.psddev.dari.db;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.psddev.dari.db.AssertUtils.assertEqualsUnordered;
import static org.junit.Assert.assertEquals;

/**
 * Created by rhseeger on 7/8/15.
 * Tests dealing with where the data comes from
 */
public class SqlDatabase_Source_Test {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDatabase_Source_Test.class);

    @ClassRule
    public static final SqlDatabaseRule res = new SqlDatabaseRule();
    @Rule
    public TestName name = new TestName();


    @BeforeClass
    public static void beforeClass() {}

    @AfterClass
    public static void afterClass() {}

    @Before
    public void before() {
        LOGGER.info("Running test [{}]", name.getMethodName());
    }

    @After
    public void after() {}


    /** .master() **/


    /** .noCache() **/


}