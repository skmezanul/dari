package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Database;
import com.psddev.dari.db.Grouping;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.UnsupportedIndexException;
import com.psddev.dari.util.Settings;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchElasticTest extends AbstractElasticTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchElasticTest.class);

    private static final String FOO = "foo";

    private static boolean searchElasticOverlapModelIndex = false;

    @Before
    public void before() {

    }

    @After
    public void deleteModels() {
        Query.from(SearchElasticModel.class).deleteAllImmediately();
        if (searchElasticOverlapModelIndex) {
            Query.from(SearchElasticOverlapModel.class).deleteAllImmediately();
        }
    }

    @Test
    public void testOne() throws Exception {
        SearchElasticModel search = new SearchElasticModel();
        search.eid = "939393";
        search.name = "Bill";
        search.message = "tough";
        search.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("eid matches ?", "939393")
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertEquals("939393", fooResult.get(0).eid);
        assertEquals("Bill", fooResult.get(0).name);
        assertEquals("tough", fooResult.get(0).message);
    }

    @Test
    public void oneMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = string;
            model.set.add(FOO);
            model.list.add(FOO);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("one matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).one, equalTo(FOO));
    }

    @Test
    public void setMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = FOO;
            model.set.add(string);
            model.list.add(FOO);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("set matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).set, hasSize(1));
        assertThat(fooResult.get(0).set.iterator().next(), equalTo(FOO));
    }

    @Test
    public void listMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = FOO;
            model.set.add(FOO);
            model.list.add(string);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("list matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).list, hasSize(1));
        assertThat(fooResult.get(0).list.get(0), equalTo(FOO));
    }

    @Test
    public void mapMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = FOO;
            model.set.add(FOO);
            model.list.add(FOO);
            model.map.put(string, string);
            model.save();
        });

        // note this is different from h2, but seems better since it is specific.
        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("map matches ?", FOO)
                .selectAll();

        assertThat("Size of result", fooResult, hasSize(1));
        assertThat("checking size of map", fooResult.get(0).map.size(), equalTo(1));
        assertThat("checking iterator", fooResult.get(0).map.values().iterator().next(), equalTo(FOO));
    }

    @Test
    public void anyMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = string;
            model.set.add(FOO);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("_any matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(3));
    }

    @Test
    public void wildcard() throws Exception {
        Stream.of("f", "fo", "foo").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = string;
            model.save();
        });

        assertThat(Query.from(SearchElasticModel.class).where("one matches ?", "f*").count(), equalTo(3L));
        assertThat(Query.from(SearchElasticModel.class).where("one matches ?", "fo*").count(), equalTo(2L));
        assertThat(Query.from(SearchElasticModel.class).where("one matches ?", "foo*").count(), equalTo(1L));
    }

    @Test
    public void sortRelevant() throws Exception {
        SearchElasticModel model = new SearchElasticModel();
        model.one = "foo";
        model.name = "qux";
        model.set.add("qux");
        model.list.add("qux");
        model.map.put("qux", "qux");
        model.eid = "1";
        model.save();

        model = new SearchElasticModel();
        model.one = "west";
        model.name = "west";
        model.set.add("west");
        model.list.add(FOO);
        model.map.put("west", "west");
        model.eid = "2";
        model.save();

        model = new SearchElasticModel();
        model.one = "qux";
        model.name = "west";
        model.set.add("west");
        model.list.add("qux");
        model.map.put("qux", "qux");
        model.eid = "3";
        model.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("_any matches ?", FOO)
                .sortRelevant(10.0, "one matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(2));

        assertThat("check 0 and 1", fooResult.get(0).eid, is(equalTo("1")));
        assertThat("check 1 and 2", fooResult.get(1).eid, is(equalTo("2")));
    }

    @Test
    public void testSortString() throws Exception {

        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchElasticModel model = new SearchElasticModel();
            model.one = string;
            model.set.add(FOO);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("one")
                .selectAll();

        assertThat("check size", fooResult, hasSize(3));
        assertThat("check 0 and 1 order", fooResult.get(0).one, lessThan(fooResult.get(1).one));
        assertThat("check 1 and 2 order", fooResult.get(1).one, lessThan(fooResult.get(2).one));
    }

    @Test(expected = UnsupportedIndexException.class)
    public void testSortStringOneField() throws Exception {

        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("neverIndexed")
                .selectAll();
    }

    @Test(expected = Query.NoFieldException.class)
    public void testSortStringNoSuchField() throws Exception {

        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("nine")
                .selectAll();
    }


    @Test
    public void testReadAllAt2() throws Exception {

        Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "searchMaxRows", "2");

        for (int i = 0; i < 50; i++) {
            SearchElasticModel model = new SearchElasticModel();
            model.f = (float) i;
            model.save();
        }

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(50));

        Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "searchMaxRows", "1000");
    }

    @Test
    public void testSortFloat() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(3));
        assertThat("check 0 and 1 order", fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));
    }

    @Test
    public void testQueryExtension() throws Exception {
        SearchElasticModel search = new SearchElasticModel();
        search.eid = "111111";
        search.name = "Bill";
        search.message = "Welcome";
        search.save();

        List<SearchElasticModel> r = Query
                .from(SearchElasticModel.class)
                .where("eid matches ?", "111111")
                .selectAll();

        assertThat(r, notNullValue());
        assertThat(r, hasSize(1));
        assertEquals("Bill", r.get(0).getName());
        assertEquals("Welcome", r.get(0).getMessage());
    }

    @Test
    public void testReferenceAscending() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel ref = new SearchElasticModel();
            ref.f = f;
            ref.save();
            SearchElasticModel model = new SearchElasticModel();
            //model.f = f;
            model.setReference(ref);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("reference/f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(6));
        assertThat("check 0 and 1 order", fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));
    }

    @Test
    public void testFloatGroupBy() throws Exception {
        Stream.of(1.0f,2.0f,3.0f,2.0f,3.0f,3.0f).forEach((Float f) -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.num = f.intValue();
            model.save();
        });

        List<Grouping<SearchElasticModel>> groupings = Query.from(SearchElasticModel.class).groupBy("f");

        assertThat("check size", groupings, hasSize(3));

        groupings.forEach(g -> {
            String keyLetter = (String) g.getKeys().get(0);

            assertThat(
                    keyLetter + " check",
                    g.getCount(),
                    is((long) Math.round(Float.parseFloat(keyLetter))));
        });

        List<Grouping<SearchElasticModel>> ranges = Query.from(SearchElasticModel.class).groupBy("num(1,4,1)");
        assertThat("check size", ranges, hasSize(3));
        assertThat("1st check " + ranges.get(0).getKeys().get(0),
                ranges.get(0).getCount(),
                is((long) 1));
        assertThat("2nd check " + ranges.get(1).getKeys().get(0),
                ranges.get(1).getCount(),
                is((long) 2));
        assertThat("3rd check " + ranges.get(2).getKeys().get(0),
                ranges.get(2).getCount(),
                is((long) 3));

    }

    @Test
    public void testDateNewestBoost() throws Exception {
        Stream.of(new java.util.Date(), DateUtils.addHours(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -10)).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortNewest(2.0, "post_date")
                .selectAll();

        assertThat("check size", fooResult, hasSize(4));
        assertThat("check 0 and 1 order", fooResult.get(0).post_date.getTime(), greaterThan(fooResult.get(1).post_date.getTime()));
        assertThat("check 1 and 2 order", fooResult.get(1).post_date.getTime(), greaterThan(fooResult.get(2).post_date.getTime()));
        assertThat("check 2 and 3 order", fooResult.get(2).post_date.getTime(), greaterThan(fooResult.get(3).post_date.getTime()));
    }

    @Test
    public void testDateLessthan() throws Exception {
        Date begin = new java.util.Date();
        Stream.of(begin,
                DateUtils.addHours(begin, -5),
                DateUtils.addDays(begin, -5),
                DateUtils.addDays(begin, -10)).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("post_date lessthan ?", begin)
                .selectAll();

        // should not include the to:
        assertThat("check size", fooResult, hasSize(3));

        List<SearchElasticModel> fooResult1 = Query
                .from(SearchElasticModel.class)
                .where("post_date lessthan ?", DateUtils.addSeconds(begin, 1))
                .selectAll();
        assertThat("check size", fooResult1, hasSize(4));
    }

    @Test
    public void testDateGreaterthan() throws Exception {
        Date begin = new java.util.Date();
        Stream.of(begin,
                DateUtils.addHours(begin, 1),
                DateUtils.addDays(begin, 1),
                DateUtils.addDays(begin, 2)).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("post_date greaterthan ?", begin)
                .selectAll();

        // should not include the from:
        assertThat("check size", fooResult, hasSize(3));

        List<SearchElasticModel> fooResult1 = Query
                .from(SearchElasticModel.class)
                .where("post_date greaterthan ?", DateUtils.addSeconds(begin, -1))
                .selectAll();
        assertThat("check size", fooResult1, hasSize(4));
    }

    @Test
    public void testSortAscending() throws Exception {

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("_id")
                .selectAll();

        assertThat("check size", fooResult, hasSize(0));
    }

    @Test
    public void testDateOldestBoost() throws Exception {
        Stream.of(new java.util.Date(), DateUtils.addHours(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -10)).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortOldest(2.0, "post_date")
                .selectAll();

        assertThat("check size", fooResult, hasSize(4));
        assertThat("check 0 and 1 order", fooResult.get(0).post_date.getTime(), lessThan(fooResult.get(1).post_date.getTime()));
        assertThat("check 1 and 2 order", fooResult.get(1).post_date.getTime(), lessThan(fooResult.get(2).post_date.getTime()));
        assertThat("check 2 and 3 order", fooResult.get(2).post_date.getTime(), lessThan(fooResult.get(3).post_date.getTime()));
    }

    @Test
    public void testDateOldestBoostRelevant() throws Exception {
        Stream.of(new java.util.Date(), DateUtils.addHours(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -10)).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortOldest(2.0, "post_date").sortRelevant(10.0, "post_date matches ?", new java.util.Date())
                .selectAll();

        assertThat("check size", fooResult, hasSize(4));
        assertThat("check 0 and 1 order", fooResult.get(0).post_date.getTime(), lessThan(fooResult.get(1).post_date.getTime()));
        assertThat("check 1 and 2 order", fooResult.get(1).post_date.getTime(), lessThan(fooResult.get(2).post_date.getTime()));
        assertThat("check 2 and 3 order", fooResult.get(2).post_date.getTime(), lessThan(fooResult.get(3).post_date.getTime()));
    }

    @Test
    public void testNumberSort() throws Exception {
        SearchElasticModel model = new SearchElasticModel();
        model.num = 1;
        model.b = 0x30;
        model.d = 1.0;
        model.f = 1.0f;
        model.l = 1L;
        model.shortType = 1;
        model.save();

        SearchElasticOverlapModel model2 = new SearchElasticOverlapModel();
        model2.num = 2;
        model2.b = 0x31;
        model2.d = 2.0;
        model2.f = "b";
        model2.l = 2L;
        model2.shortType = 2;
        model2.save();
        searchElasticOverlapModelIndex = true;

        SearchElasticModel model3 = new SearchElasticModel();
        model3.num = 3;
        model3.b = 0x32;
        model3.d = 3.0;
        model3.f = 3.0f;
        model3.l = 3L;
        model3.shortType = 3;
        model3.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("f")
                .selectAll();

        List<SearchElasticOverlapModel> fooResult2 = Query
                .from(SearchElasticOverlapModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size SearchElasticModel", fooResult, hasSize(2));

        assertThat("check size SearchElasticOverlapModel", fooResult2, hasSize(1));
    }

    @Test
    public void testOverlapElasticTypes() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        Stream.of("1.0","2.0","3.0").forEach(f -> {
            SearchElasticOverlapModel model2 = new SearchElasticOverlapModel();
            model2.f = f;
            model2.save();
        });
        searchElasticOverlapModelIndex = true;

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .selectAll();

        List<SearchElasticOverlapModel> fooResult2 = Query
                .from(SearchElasticOverlapModel.class)
                .selectAll();

        assertThat("check size SearchElasticModel", fooResult, hasSize(3));

        assertThat("check size SearchElasticOverlapModel", fooResult2, hasSize(3));
    }

    @Test
    public void testSortOverlapElasticTypes() throws Exception {
        Stream.of(1.0f,3.0f,2.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        Stream.of("a","c","b").forEach(f -> {
            SearchElasticOverlapModel model2 = new SearchElasticOverlapModel();
            model2.f = f;
            model2.save();
        });
        searchElasticOverlapModelIndex = true;

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size all", fooResult, hasSize(3));
        assertThat("check 0 and 1 order",  fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));

        List<SearchElasticOverlapModel> fooResult2 = Query
                .from(SearchElasticOverlapModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size all", fooResult2, hasSize(3));
        assertThat("check 0 and 1 order",  fooResult2.get(0).f, lessThan(fooResult2.get(1).f));
        assertThat("check 1 and 2 order", fooResult2.get(1).f, lessThan(fooResult2.get(2).f));

    }

    @Test
    public void testTimeout() throws Exception {
        Stream.of(new java.util.Date()).forEach(d -> {
            SearchElasticModel model = new SearchElasticModel();
            model.post_date = d;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortOldest(2.0, "post_date")
                .timeout(500.0)
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testLogin() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");;
        model.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("loginTokens/token equalsany ?",UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

   @Test
    public void testComplexQuery() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");;
        model.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("(loginTokens/token notequalsall missing and (f equalsany missing and num equalsany missing "
                        + "and set equalsany missing and list equalsany missing and _type notequalsall ?"
                        + ") and _any matchesany '*')", UUID.fromString("68a66f18-b668-418b-af69-8dafa632529"))
                .selectAll();

        // _type notequalsall ?

        assertThat("check size", fooResult, hasSize(1));
    }


}
