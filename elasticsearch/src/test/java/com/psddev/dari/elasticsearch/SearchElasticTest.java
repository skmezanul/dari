package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Database;
import com.psddev.dari.db.DatabaseEnvironment;
import com.psddev.dari.db.Grouping;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectMethod;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.db.UnsupportedIndexException;
import com.psddev.dari.util.PaginatedResult;
import com.psddev.dari.util.Settings;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
    public void testSortStringNeverIndexed() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchElasticModel model = new SearchElasticModel();
            model.f = f;
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("neverIndexed")
                .selectAll();

         assertThat("check size", fooResult, hasSize(3));
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
            model.setReference(ref);
            model.save();
        });

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(6));
        assertThat("check 0 and 1 order", fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));
    }

    @Test
    public void testReferenceGetTypeRef() throws Exception {
        SearchElasticModel ref = new SearchElasticModel();
        ref.f = 1.0f;
        ref.save();
        SearchElasticModel model = new SearchElasticModel();
        model.setReference(ref);
        model.save();

        // [user equalsany 0000015a-ab7e-d2d3-a97f-bffedfc30000]

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("reference equalsany ?", ref.getId())
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
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
    public void testSortAscendingDirectory() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.setOne("/");
        model.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("one equals ?", "/")
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
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
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchElasticModel> fooResult = Query
                .from(SearchElasticModel.class)
                .where("loginTokens/token equalsany ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testUUIDlt() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchElasticModel> fooResult = Query.from(SearchElasticModel.class)
                .where("loginTokens/token < ?", new UUID(0,0))
                .selectAll();

        assertThat("check size", fooResult, hasSize(0));
    }

    @Test
    public void testUUIDlte() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchElasticModel> fooResult = Query.from(SearchElasticModel.class)
                .where("loginTokens/token <= ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }


    @Test
    public void testUUIDgt() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchElasticModel> fooResult = Query.from(SearchElasticModel.class)
                .where("loginTokens/token > ?", new UUID(0,0))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testUUIDgte() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchElasticModel> fooResult = Query.from(SearchElasticModel.class)
                .where("loginTokens/token >= ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesany() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchElasticModel.class)
                .where("loginTokens/token matchesany ?", new UUID(0,0))
                .selectAll();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesall() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchElasticModel.class)
                .where("loginTokens/token matchesall ?", new UUID(0,0))
                .selectAll();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesany2() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchElasticModel.class)
                .where("loginTokens/token matchesany ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesall2() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchElasticModel.class)
                .where("loginTokens/token matchesall ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();
    }

   @Test
    public void testComplexQuery() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
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

    @Test
    public void testQuery() throws Exception {

        SearchElasticModel model = new SearchElasticModel();
        model.one = "test";
        model.save();
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "test";
        model1.save();

        Query<SearchElasticModel> query = Query.from(SearchElasticModel.class);

        List<SearchElasticModel> selectAll = query.selectAll();
        assertThat("check selectAll", selectAll, hasSize(2));

        PaginatedResult<SearchElasticModel> select = query.select(0, 10);
        assertEquals(2, select.getCount());

        SearchElasticModel first = query.first();
        assertThat("check size", first, notNullValue());

        Iterable<SearchElasticModel> iter = query.iterable(10);
        int i = 0;
        for (SearchElasticModel s : iter) {
            i++;
        }
        assertThat("check iter", i, is(2));

        List<Grouping<SearchElasticModel>> groupBy = Query.from(SearchElasticModel.class).groupBy("one");
        Iterator<Grouping<SearchElasticModel>> iGroup = groupBy.iterator();

        while(iGroup.hasNext()) {
            Grouping element = iGroup.next();
            assertEquals(2, element.getCount());
        }

        Query<SearchElasticModel> searchQuery = Query.from(SearchElasticModel.class).where("one = ?", "test");

        List<SearchElasticModel> search = searchQuery.selectAll();
        assertThat("check where", search, hasSize(2));

        ObjectType type = ObjectType.getInstance(SearchElasticModel.class);

        List<Object> search2 = Query.fromType(type).selectAll();
        assertThat("check fromType", search2, hasSize(2));
    }

    @Test
    public void testGlobals() {
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "test";
        model1.save();

        List<SearchElasticModel> query = Query.from(SearchElasticModel.class).selectAll();

        Database defaultDatabase = Database.Static.getDefault();
        DatabaseEnvironment environment = defaultDatabase.getEnvironment();
        List<ObjectField> globalFields = environment.getFields();
        assertThat(globalFields.isEmpty(), is(false));
    }

    @Test
    public void testModification() {
        Date begin = new java.util.Date();
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "test";
        model1.as(ElasticModification.class).setUpdateDate(begin);
        model1.save();

        List<SearchElasticModel> sem = Query.from(SearchElasticModel.class).selectAll();
        assertThat("check testModification", sem, hasSize(1));

        List<SearchElasticModel> sem2 = Query.from(SearchElasticModel.class)
                .where("cms.content.updateDate > ?", DateUtils.addHours(new java.util.Date(), -1))
                .sortAscending("cms.content.updateDate")
                .selectAll();
        assertThat("check testModification", sem2, hasSize(1));

        List<SearchElasticModel> sem3 = Query.from(SearchElasticModel.class)
                .where("cms.content.updateDate = ?", begin)
                .sortAscending("cms.content.updateDate")
                .selectAll();
        assertThat("check testModification", sem2, hasSize(1));
    }

    @Test
    public void testMatchesAll() {
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "test headline story";
        model1.save();

        SearchElasticModel model2 = new SearchElasticModel();
        model2.one = "another story";
        model2.save();

        List<SearchElasticModel> sem = Query.from(SearchElasticModel.class).where("one matchesany ?", "headline").selectAll();
        assertThat("check matchesany", sem, hasSize(1));

        List<SearchElasticModel> sem1 = Query.from(SearchElasticModel.class).where("one matchesall ?", "headline").selectAll();
        assertThat("check matchesall", sem1, hasSize(1));

        List<String> many = new ArrayList<>();
        many.add("test");
        many.add("headline");
        List<SearchElasticModel> sem2 = Query.from(SearchElasticModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem2, hasSize(1));
        List<SearchElasticModel> sem3 = Query.from(SearchElasticModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem3, hasSize(1));

        List<String> many2 = new ArrayList<>();
        many.add("test");
        many.add("story");
        List<SearchElasticModel> sem4 = Query.from(SearchElasticModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem4, hasSize(2));
        List<SearchElasticModel> sem5 = Query.from(SearchElasticModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem5, hasSize(1));
    }

    @Test
    public void testMatchesAllCase() {
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "TeSt HeAdLiNe StOrY";
        model1.save();

        SearchElasticModel model2 = new SearchElasticModel();
        model2.one = "AnOtHeR StOrY";
        model2.save();

        List<SearchElasticModel> zeroAny = Query.from(SearchElasticModel.class).where("one matchesany ?", (Object) null).selectAll();
        assertThat("check matchesany", zeroAny, hasSize(0));

        List<SearchElasticModel> zeroAll = Query.from(SearchElasticModel.class).where("one matchesany ?", (Object) null).selectAll();
        assertThat("check matchesany", zeroAll, hasSize(0));

        List<SearchElasticModel> sem = Query.from(SearchElasticModel.class).where("one matchesany ?", "headline").selectAll();
        assertThat("check matchesany", sem, hasSize(1));

        List<SearchElasticModel> sem1 = Query.from(SearchElasticModel.class).where("one matchesall ?", "headline").selectAll();
        assertThat("check matchesall", sem1, hasSize(1));

        List<SearchElasticModel> contains = Query.from(SearchElasticModel.class).where("one contains ?", "headline").selectAll();
        assertThat("check matchesall", contains, hasSize(1));

        List<String> many = new ArrayList<>();
        many.add("test");
        many.add("headline");
        List<SearchElasticModel> sem2 = Query.from(SearchElasticModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem2, hasSize(1));
        List<SearchElasticModel> sem3 = Query.from(SearchElasticModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem3, hasSize(1));

        List<String> many2 = new ArrayList<>();
        many.add("test");
        many.add("story");
        List<SearchElasticModel> sem4 = Query.from(SearchElasticModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem4, hasSize(2));
        List<SearchElasticModel> sem5 = Query.from(SearchElasticModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem5, hasSize(1));
    }

    @Test
    public void testSortNumber() {

        SearchElasticModel model1 = new SearchElasticModel();
        model1.f = 1.0f;
        model1.d = 1.0d;
        model1.num = 100;
        model1.l = 100L;
        model1.shortType = (short) 5;
        model1.save();

        SearchElasticModel model2 = new SearchElasticModel();
        model2.f = 1.1f;
        model2.d = 1.1d;
        model2.num = 200;
        model2.l = 200L;
        model2.shortType = (short) 6;
        model2.save();

        SearchElasticModel model3 = new SearchElasticModel();
        model3.save();

        List<SearchElasticModel> floatNumber = Query.from(SearchElasticModel.class).sortAscending("f").selectAll();
        assertThat("check sort float", floatNumber, hasSize(3));
        assertThat("check 0 and 1 order",  floatNumber.get(0).f, lessThan(floatNumber.get(1).f));
        assertThat(floatNumber.get(2).f, nullValue());

        List<SearchElasticModel> doubleNumber = Query.from(SearchElasticModel.class).sortAscending("d").selectAll();
        assertThat("check sort double", doubleNumber, hasSize(3));
        assertThat("check 0 and 1 order",  doubleNumber.get(0).d, lessThan(doubleNumber.get(1).d));
        assertThat(doubleNumber.get(2).d, nullValue());

        List<SearchElasticModel> intNumber = Query.from(SearchElasticModel.class).sortAscending("num").selectAll();
        assertThat("check sort int", intNumber, hasSize(3));
        assertThat("check 0 and 1 order",  intNumber.get(0).num, lessThan(intNumber.get(1).num));
        assertThat(intNumber.get(2).num, nullValue());

        List<SearchElasticModel> longNumber = Query.from(SearchElasticModel.class).sortAscending("l").selectAll();
        assertThat("check sort long", longNumber, hasSize(3));
        assertThat("check 0 and 1 order",  longNumber.get(0).l, lessThan(longNumber.get(1).l));
        assertThat(longNumber.get(2).l, nullValue());

        List<SearchElasticModel> shortNumber = Query.from(SearchElasticModel.class).sortAscending("shortType").selectAll();
        assertThat("check sort short", shortNumber, hasSize(3));
        assertThat("check 0 and 1 order",  shortNumber.get(0).shortType, lessThan(shortNumber.get(1).shortType));
        assertThat(shortNumber.get(2).shortType, nullValue());
    }

    @Test
    public void testSortTypeFieldAll() {
        SearchElasticModel model1 = new SearchElasticModel();
        model1.one = "test headline story";
        model1.save();

        SearchElasticModel model2 = new SearchElasticModel();
        model2.one = "another story";
        model2.save();

        String sort = ObjectType.getInstance(SearchElasticModel.class).getInternalName() + "/one";

        List<SearchElasticModel> sem = Query.from(SearchElasticModel.class).sortAscending(sort).selectAll();
        assertThat("check matchesany", sem, hasSize(2));
        assertThat("check 0 and 1 order",  sem.get(0).one, lessThan(sem.get(1).one));
        assertThat("equals check", sem.get(0).one, is("another story"));
        assertThat("equals check", sem.get(1).one, is("test headline story"));
    }

    @Test
    public void testIndexMethod() {
        MethodIndexElasticModel model = new MethodIndexElasticModel();
        model.setName("story");
        model.save();

        List<MethodIndexElasticModel> tagg = Query.from(MethodIndexElasticModel.class).where("taggable.getFoo2 = ?", "Foo2").selectAll();
        assertThat("check IndexMethod", tagg, hasSize(1));

        List<MethodIndexElasticModel> sem = Query.from(MethodIndexElasticModel.class).where("getFoo = ?", "Foo").selectAll();
        assertThat("check IndexMethod", sem, hasSize(1));

        List<MethodIndexElasticModel> sem1 = Query.from(MethodIndexElasticModel.class).where("getInfo matches ?", "larger").selectAll();
        assertThat("check IndexMethod", sem1, hasSize(1));

        List<MethodIndexElasticModel> sem2 = Query.from(MethodIndexElasticModel.class).where("getPrefixName = ?", "defaultstory").selectAll();
        assertThat("check IndexMethod", sem2, hasSize(1));

        List<Grouping<MethodIndexElasticModel>> groupL = Query.from(MethodIndexElasticModel.class).groupBy("getNameFirstLetter");
        assertThat("check Grouping / agg", groupL, hasSize(1));
        for (Grouping g : groupL) {
            if (g.getKeys().contains("s")) {
                assertThat(g.getCount(), is((long) 1));
            }
        }
    }

    // add multi level

    @Test
    public void testIndexes() {

        SearchElasticModel model = new SearchElasticModel();
        model.setOne("story");
        model.save();

        MethodIndexElasticModel model1 = new MethodIndexElasticModel();
        model1.setName("story");
        model1.save();

        State s = model1.getState();
        List<ObjectMethod> methods  = s.getType().getMethods();
        methods.addAll(Database.Static.getDefault().getEnvironment().getMethods());

        List<String> lMethods = new ArrayList<>();
        for (ObjectMethod method : methods) {
            lMethods.add(method.getUniqueName());
        }

        assertThat("check methods Indexed", lMethods, hasSize(6));
        assertThat("check methods Indexed", lMethods,
                hasItems("com.psddev.dari.elasticsearch.MethodIndexElasticModel/getName",
                         "com.psddev.dari.elasticsearch.MethodIndexElasticModel/getFoo",
                         "com.psddev.dari.elasticsearch.MethodIndexElasticModel/taggable.getFoo2",
                         "com.psddev.dari.elasticsearch.MethodIndexElasticModel/getInfo",
                         "com.psddev.dari.elasticsearch.MethodIndexElasticModel/getNameFirstLetter",
                         "com.psddev.dari.elasticsearch.MethodIndexElasticModel/getPrefixName"));
    }

    @Test
    public void testComplexTaggedIndexMethod() {
        MethodComplexModel model1 = new MethodComplexModel();
        model1.setGivenName("Mickey");
        model1.setSurname("Mouse");
        model1.save();

        List<MethodComplexModel> tagList = Query.from(MethodComplexModel.class).where("taggable.getNames = ?", "Mickey").selectAll();
        assertThat("check tagged Mickey", tagList, hasSize(1));

        List<MethodComplexModel> tagList2 = Query.from(MethodComplexModel.class).where("taggable.getNames matches ?", "MiCkEy").selectAll();
        assertThat("check tagged Mickey", tagList2, hasSize(1));

        List<MethodComplexModel> tagSet = Query.from(MethodComplexModel.class).where("taggable.getSetNames = ?", "Mickey").selectAll();
        assertThat("check taggedSet Mickey", tagSet, hasSize(1));

        List<MethodComplexModel> tagSet2 = Query.from(MethodComplexModel.class).where("taggable.getSetNames matches ?", "mickey").selectAll();
        assertThat("check tagged matches", tagSet2, hasSize(1));
    }

    @Test
    public void testDenormalizedTags() {
        ElasticTag t = new ElasticTag();
        t.setName("pizza");
        t.save();

        ElasticTag nt = new ElasticTag();
        t.setName("noindex");
        nt.save();

        DenormalizedReferenceModel model1 = new DenormalizedReferenceModel();
        model1.setName("Mickey");
        model1.setIndexedTag(t);
        model1.setUnindexedTag(nt);
        model1.save();

        List<DenormalizedReferenceModel> tagList = Query.from(DenormalizedReferenceModel.class).where("_any matches ?", "pizza").selectAll();
        assertThat("check size", tagList, hasSize(1));

        List<DenormalizedReferenceModel> tagList2 = Query.from(DenormalizedReferenceModel.class).where("_any matches ?", "noindex").selectAll();
        assertThat("check size", tagList2, hasSize(0));

        List<DenormalizedReferenceModel> tagList3 = Query.from(DenormalizedReferenceModel.class).where("taggable.indexedTag/name = ?", "pizza").selectAll();
        assertThat("check size", tagList3, hasSize(1));

        // should not match
        List<DenormalizedReferenceModel> tagList4 = Query.from(DenormalizedReferenceModel.class).where("taggable.indexedTag/name matches ?", "pizza").selectAll();
        assertThat("check size", tagList4, hasSize(1));
    }
}

