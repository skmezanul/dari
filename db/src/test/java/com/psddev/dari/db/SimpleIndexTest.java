package com.psddev.dari.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.psddev.dari.util.UuidUtils;

public class SimpleIndexTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleIndexTest.class);

    private static List<TestDatabase> TEST_DATABASES;
    private static List<Database> DATABASES;

    private static String STRING =              "string";
    private static Boolean BOOLEAN_OBJECT =     true;
    private static boolean BOOLEAN_PRIMITIVE =  true;
    private static Integer INT_OBJECT =         3;
    private static int INT_PRIMITIVE =          4;
    private static Long LONG_OBJECT =           5L;
    private static long LONG_PRIMITIVE =        6L;
    private static Float FLOAT_OBJECT =         7.0f;
    private static float FLOAT_PRIMITIVE =      8.0f;
    private static Double DOUBLE_OBJECT =       9.0;
    private static double DOUBLE_PRIMITIVE =    10.0;
    private static Date DATE =                  new Date();
    private static Location LOCATION =          new Location(3, 14);
    private static URI URI;                     static {
        try {
            URI = new URI("http://dariframework.org");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    private static URL URL;                     static {
        try {
            URL = new URL("http://dariframework.org");
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        }
    }
    private static UUID UUID = UuidUtils.createSequentialUuid();
    private static Locale LOCALE = Locale.getDefault();

    @BeforeClass
    public static void beforeClass() {

        TEST_DATABASES = DatabaseTestUtils.getNewDefaultTestDatabaseInstances();
        DATABASES = new ArrayList<Database>();

        for (TestDatabase testDb : TEST_DATABASES) {
            Database db = testDb.get();
            DATABASES.add(db);
        }

        LOGGER.info("Running tests againsts " + TEST_DATABASES.size() + " databases.");
    }

    @AfterClass
    public static void afterClass() {
        if (TEST_DATABASES != null) for (TestDatabase testDb : TEST_DATABASES) {
            testDb.close();
        }
    }

    @Before
    public void before() {
    }

    @After
    public void after() {
    }

    @Test
    public void stringIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testString = STRING;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testString = ?", STRING).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testString, expected.testString);
        }
    }

    @Test
    @Ignore
    public void booleanObjectIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testBooleanObject = BOOLEAN_OBJECT;
            expected.save();

            Query<TestRecord> q = Query.from(TestRecord.class).using(db).where("testBooleanObject = ?", BOOLEAN_OBJECT);
            System.out.println(((SqlDatabase)db).buildSelectStatement(q));
            TestRecord actual = q.first();

            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testBooleanObject, expected.testBooleanObject);
        }
    }

    @Test
    @Ignore
    public void booleanPrimitiveIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testBooleanPrimitive = BOOLEAN_PRIMITIVE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testBooleanPrimitive = ?", BOOLEAN_PRIMITIVE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testBooleanPrimitive, expected.testBooleanPrimitive);
        }
    }

    @Test
    public void intObjectIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testIntObject = INT_OBJECT;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testIntObject = ?", INT_OBJECT).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testIntObject, expected.testIntObject);
        }
    }

    @Test
    public void intPrimitiveIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testIntPrimitive = INT_PRIMITIVE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testIntPrimitive = ?", INT_PRIMITIVE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testIntPrimitive, expected.testIntPrimitive);
        }
    }

    @Test
    public void longObjectIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testLongObject = LONG_OBJECT;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testLongObject = ?", LONG_OBJECT).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testLongObject, expected.testLongObject);
        }
    }

    @Test
    public void longPrimitiveIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testLongPrimitive = LONG_PRIMITIVE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testLongPrimitive = ?", LONG_PRIMITIVE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testLongPrimitive, expected.testLongPrimitive);
        }
    }

    @Test
    public void floatObjectIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testFloatObject = FLOAT_OBJECT;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testFloatObject = ?", FLOAT_OBJECT).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testFloatObject, expected.testFloatObject);
        }
    }

    @Test
    public void floatPrimitiveIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testFloatPrimitive = FLOAT_PRIMITIVE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testFloatPrimitive = ?", FLOAT_PRIMITIVE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testFloatPrimitive, expected.testFloatPrimitive, 0L);
        }
    }

    @Test
    public void doubleObjectIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testDoubleObject = DOUBLE_OBJECT;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testDoubleObject = ?", DOUBLE_OBJECT).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testDoubleObject, expected.testDoubleObject);
        }
    }

    @Test
    public void doublePrimitiveIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testDoublePrimitive = DOUBLE_PRIMITIVE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testDoublePrimitive = ?", DOUBLE_PRIMITIVE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testDoublePrimitive, expected.testDoublePrimitive, 0L);
        }
    }

    @Test
    public void dateIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testDate = DATE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testDate = ?", DATE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testDate, expected.testDate);
        }
    }

    @Test
    @Ignore
    public void locationIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testLocation = LOCATION;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testLocation = ?", LOCATION).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testLocation, expected.testLocation);
        }
    }

    @Test
    public void uriIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testUri = URI;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testUri = ?", URI).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testUri, expected.testUri);
        }
    }

    @Test
    public void urlIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testUrl = URL;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testUrl = ?", URL).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testUrl, expected.testUrl);
        }
    }

    @Test
    public void uuidIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testUuid = UUID;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testUuid = ?", UUID).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testUuid, expected.testUuid);
        }
    }

    @Test
    public void localeIndex() {
        for (Database db : DATABASES) {
            TestRecord expected = TestRecord.getInstance(db);
            expected.testLocale = LOCALE;
            expected.save();

            TestRecord actual = Query.from(TestRecord.class).using(db).where("testLocale = ?", LOCALE).first();
            assertNotNull(actual);
            assertEquals(actual, expected);
            assertEquals(actual.testLocale, expected.testLocale);
        }
    }

    @Test
    public void testPhrase() {
        String test = "Hubble Astronomers Develop is a big item";

        for (Database db : DATABASES) {
            if (db instanceof SolrDatabase) {

//                db.getEnvironment().refreshGlobals();
//                db.getEnvironment().refreshTypes();
//                db.getEnvironment().refreshGlobals();
//                db.getEnvironment().refreshTypes();

                TestRecord expected = TestRecord.getInstance(db);
                expected.testString = test;
                expected.save();

                TestRecord actual = Query.from(TestRecord.class).using(db).where("testString matches ?", test).first();
                assertNotNull(actual);
                assertEquals(actual, expected);
                assertEquals(actual.testString, expected.testString);

                TestRecord actual2 = Query.from(TestRecord.class).using(db).where("testString matches ?", "develop").first();
                assertNotNull(actual2);
                assertEquals(actual2, expected);
                assertEquals(actual2.testString, expected.testString);

                QueryPhrase qp = new QueryPhrase();
                qp.setPhrase("Hubble Astronomers Develop");
                TestRecord actual1 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).first();
                assertNotNull(actual1);
                assertEquals(actual1, expected);
                assertEquals(actual1.testString, expected.testString);

                qp.setPhrase("Astronomers Develop");
                TestRecord actual4 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).first();
                assertNotNull(actual4);
                assertEquals(actual4, expected);
                assertEquals(actual4.testString, expected.testString);

                qp.setPhrase("Develop Astronomers");
                TestRecord actual5 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).first();
                assertNull(actual5);

                qp.setPhrase("Develop Astronomers");
                qp.setSlop(2.0f);
                TestRecord actual6 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).first();
                assertNotNull(actual6);
                assertEquals(actual6, expected);
                assertEquals(actual6.testString, expected.testString);

                qp.setPhrase("Develop item");
                qp.setSlop(3.0f);
                TestRecord actual7 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).first();
                assertNotNull(actual7);
                assertEquals(actual7, expected);
                assertEquals(actual7.testString, expected.testString);

                TestRecord expected2 = TestRecord.getInstance(db);
                expected2.testString = "Hubble are Astronomers that eat";
                expected2.save();

                qp.setPhrase("Hubble Astronomers");
                qp.setSlop(1.0f);
                qp.setBoost(10.0f);
                TestRecord actual8 = Query.from(TestRecord.class).using(db).where("testString matches ?", qp).
                        or("testString matches ?", "eat")
                        .sortRelevant(10.0d, "testString matches ?", qp).first();
                assertNotNull(actual8);
                assertEquals(actual8, expected);
                assertEquals(actual8.testString, expected.testString);
            }
        }
    }

    static class TestRecord extends Record {

        public static TestRecord getInstance(Database db) {
            TestRecord tr = new TestRecord();
            tr.getState().setDatabase(db);
            return tr;
        }

        @Indexed String testString;
        @Indexed Boolean testBooleanObject;
        @Indexed boolean testBooleanPrimitive;
        @Indexed Integer testIntObject;
        @Indexed int testIntPrimitive;
        @Indexed Long testLongObject;
        @Indexed long testLongPrimitive;
        @Indexed Float testFloatObject;
        @Indexed float testFloatPrimitive;
        @Indexed Double testDoubleObject;
        @Indexed double testDoublePrimitive;
        @Indexed Date testDate;
        @Indexed Location testLocation;
        @Indexed URI testUri;
        @Indexed URL testUrl;
        @Indexed UUID testUuid;
        @Indexed Locale testLocale;
    }
}
