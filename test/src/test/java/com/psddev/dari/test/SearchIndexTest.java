package com.psddev.dari.test;

import com.psddev.dari.db.Database;
import com.psddev.dari.db.DatabaseEnvironment;
import com.psddev.dari.db.Grouping;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectMethod;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.elasticsearch.ElasticsearchDatabase;
import com.psddev.dari.util.ObjectUtils;
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

import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category({com.psddev.dari.test.ElasticTest.class})
public class SearchIndexTest extends AbstractTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexTest.class);

    private static final String FOO = "foo";

    private static boolean SearchOverlapModelIndex = false;

    @Before
    public void before() {

    }

    @After
    public void deleteModels() {
        Query.from(SearchIndexModel.class).deleteAllImmediately();
        if (SearchOverlapModelIndex) {
            Query.from(SearchOverlapModel.class).deleteAllImmediately();
        }
    }

    @Test
    public void testOne() throws Exception {
        SearchIndexModel search = new SearchIndexModel();
        search.eid = "939393";
        search.name = "Bill";
        search.message = "tough";
        search.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel model = new SearchIndexModel();
            model.one = string;
            model.set.add(FOO);
            model.list.add(FOO);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("one matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).one, equalTo(FOO));
    }

    @Test
    public void setMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchIndexModel model = new SearchIndexModel();
            model.one = FOO;
            model.set.add(string);
            model.list.add(FOO);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("set matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).set, hasSize(1));
        assertThat(fooResult.get(0).set.iterator().next(), equalTo(FOO));
    }

    @Test
    public void listMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchIndexModel model = new SearchIndexModel();
            model.one = FOO;
            model.set.add(FOO);
            model.list.add(string);
            model.map.put(FOO, FOO);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("list matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(1));
        assertThat(fooResult.get(0).list, hasSize(1));
        assertThat(fooResult.get(0).list.get(0), equalTo(FOO));
    }

    @Test
    public void mapMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchIndexModel model = new SearchIndexModel();
            model.one = FOO;
            model.set.add(FOO);
            model.list.add(FOO);
            model.map.put(string, string);
            model.save();
        });

        // note this is different from h2, but seems better since it is specific.
        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("map matches ?", FOO)
                .selectAll();

        assertThat("Size of result", fooResult, hasSize(1));
        assertThat("checking size of map", fooResult.get(0).map.size(), equalTo(1));
        assertThat("checking iterator", fooResult.get(0).map.values().iterator().next(), equalTo(FOO));
    }

    @Test
    public void anyMatches() throws Exception {
        Stream.of(FOO, "bar", "qux").forEach(string -> {
            SearchIndexModel model = new SearchIndexModel();
            model.one = string;
            model.set.add(FOO);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("_any matches ?", FOO)
                .selectAll();

        assertThat(fooResult, hasSize(3));
    }

    @Test
    public void wildcard() throws Exception {
        Stream.of("f", "fo", "foo").forEach(string -> {
            SearchIndexModel model = new SearchIndexModel();
            model.one = string;
            model.save();
        });

        assertThat(Query.from(SearchIndexModel.class).where("one matches ?", "f*").count(), equalTo(3L));
        assertThat(Query.from(SearchIndexModel.class).where("one matches ?", "fo*").count(), equalTo(2L));
        assertThat(Query.from(SearchIndexModel.class).where("one matches ?", "foo*").count(), equalTo(1L));
    }

    @Test
    public void sortRelevant() throws Exception {
        SearchIndexModel model = new SearchIndexModel();
        model.one = "foo";
        model.name = "qux";
        model.set.add("qux");
        model.list.add("qux");
        model.map.put("qux", "qux");
        model.eid = "1";
        model.save();

        model = new SearchIndexModel();
        model.one = "west";
        model.name = "west";
        model.set.add("west");
        model.list.add(FOO);
        model.map.put("west", "west");
        model.eid = "2";
        model.save();

        model = new SearchIndexModel();
        model.one = "qux";
        model.name = "west";
        model.set.add("west");
        model.list.add("qux");
        model.map.put("qux", "qux");
        model.eid = "3";
        model.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel model = new SearchIndexModel();
            model.one = string;
            model.set.add(FOO);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("one")
                .selectAll();

        assertThat("check size", fooResult, hasSize(3));
        assertThat("check 0 and 1 order", fooResult.get(0).one, lessThan(fooResult.get(1).one));
        assertThat("check 1 and 2 order", fooResult.get(1).one, lessThan(fooResult.get(2).one));
    }

    @Test
    public void testSortStringNeverIndexed() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.save();
        });

        // this should not throw exception since the field is annotated @Indexed
        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("neverIndexed")
                .selectAll();

         assertThat("check size", fooResult, hasSize(3));
    }

    @Test(expected = Query.NoFieldException.class)
    public void testSortStringNoSuchField() throws Exception {

        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("nine")
                .selectAll();
    }


    @Test
    public void testReadAllAt2() throws Exception {

        Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "searchMaxRows", "2");

        for (int i = 0; i < 50; i++) {
            SearchIndexModel model = new SearchIndexModel();
            model.f = (float) i;
            model.save();
        }

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(50));

        Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "searchMaxRows", "1000");
    }

    @Test
    public void testSortFloat() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(3));
        assertThat("check 0 and 1 order", fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));
    }

    @Test
    public void testQueryExtension() throws Exception {
        SearchIndexModel search = new SearchIndexModel();
        search.eid = "111111";
        search.name = "Bill";
        search.message = "Welcome";
        search.save();

        List<SearchIndexModel> r = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel ref = new SearchIndexModel();
            ref.f = f;
            ref.save();
            SearchIndexModel model = new SearchIndexModel();
            model.setReference(ref);
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size", fooResult, hasSize(6));
        assertThat("check 0 and 1 order", fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));
    }

    @Test
    public void testReferenceGetTypeRef() throws Exception {
        SearchIndexModel ref = new SearchIndexModel();
        ref.f = 1.0f;
        ref.save();
        SearchIndexModel model = new SearchIndexModel();
        model.setReference(ref);
        model.save();

        // [user equalsany 0000015a-ab7e-d2d3-a97f-bffedfc30000]

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("reference equalsany ?", ref.getId())
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testFloatGroupBy() throws Exception {
        Stream.of(1.0f,2.0f,3.0f,2.0f,3.0f,3.0f).forEach((Float f) -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.num = f.intValue();
            model.save();
        });

        List<Grouping<SearchIndexModel>> groupings = Query.from(SearchIndexModel.class).groupBy("f");

        assertThat("check size", groupings, hasSize(3));

        groupings.forEach(g -> {
            String keyLetter = (String) g.getKeys().get(0);

            assertThat(
                    keyLetter + " check",
                    g.getCount(),
                    is((long) Math.round(Float.parseFloat(keyLetter))));
        });

        List<Grouping<SearchIndexModel>> ranges = Query.from(SearchIndexModel.class).groupBy("num(1,4,1)");
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
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("post_date lessthan ?", begin)
                .selectAll();

        // should not include the to:
        assertThat("check size", fooResult, hasSize(3));

        List<SearchIndexModel> fooResult1 = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("post_date greaterthan ?", begin)
                .selectAll();

        // should not include the from:
        assertThat("check size", fooResult, hasSize(3));

        List<SearchIndexModel> fooResult1 = Query
                .from(SearchIndexModel.class)
                .where("post_date greaterthan ?", DateUtils.addSeconds(begin, -1))
                .selectAll();
        assertThat("check size", fooResult1, hasSize(4));
    }

    @Test
    public void testSortAscendingDirectory() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.setOne("/");
        model.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("one equals ?", "/")
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testSortAscending() throws Exception {

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("_id")
                .selectAll();

        assertThat("check size", fooResult, hasSize(0));
    }

    @Test
    public void testDateOldestBoost() throws Exception {
        Stream.of(new java.util.Date(), DateUtils.addHours(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -5), DateUtils.addDays(new java.util.Date(), -10)).forEach(d -> {
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
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
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortOldest(2.0, "post_date").sortRelevant(10.0, "post_date matches ?", new java.util.Date())
                .selectAll();

        assertThat("check size", fooResult, hasSize(4));
        assertThat("check 0 and 1 order", fooResult.get(0).post_date.getTime(), lessThan(fooResult.get(1).post_date.getTime()));
        assertThat("check 1 and 2 order", fooResult.get(1).post_date.getTime(), lessThan(fooResult.get(2).post_date.getTime()));
        assertThat("check 2 and 3 order", fooResult.get(2).post_date.getTime(), lessThan(fooResult.get(3).post_date.getTime()));
    }

    @Test
    public void testNumberSort() throws Exception {
        SearchIndexModel model = new SearchIndexModel();
        model.num = 1;
        model.b = 0x30;
        model.d = 1.0;
        model.f = 1.0f;
        model.l = 1L;
        model.shortType = 1;
        model.save();

        SearchOverlapModel model2 = new SearchOverlapModel();
        model2.num = 2;
        model2.b = 0x31;
        model2.d = 2.0;
        model2.f = "b";
        model2.l = 2L;
        model2.shortType = 2;
        model2.save();
        SearchOverlapModelIndex = true;

        SearchIndexModel model3 = new SearchIndexModel();
        model3.num = 3;
        model3.b = 0x32;
        model3.d = 3.0;
        model3.f = 3.0f;
        model3.l = 3L;
        model3.shortType = 3;
        model3.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("f")
                .selectAll();

        List<SearchOverlapModel> fooResult2 = Query
                .from(SearchOverlapModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size SearchIndexModel", fooResult, hasSize(2));

        assertThat("check size SearchOverlapModel", fooResult2, hasSize(1));
    }

    @Test
    public void testOverlapElasticTypes() throws Exception {
        Stream.of(1.0f,2.0f,3.0f).forEach(f -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.save();
        });

        Stream.of("1.0","2.0","3.0").forEach(f -> {
            SearchOverlapModel model2 = new SearchOverlapModel();
            model2.f = f;
            model2.save();
        });
        SearchOverlapModelIndex = true;

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .selectAll();

        List<SearchOverlapModel> fooResult2 = Query
                .from(SearchOverlapModel.class)
                .selectAll();

        assertThat("check size SearchIndexModel", fooResult, hasSize(3));

        assertThat("check size SearchOverlapModel", fooResult2, hasSize(3));
    }

    @Test
    public void testSortOverlapElasticTypes() throws Exception {
        Stream.of(1.0f,3.0f,2.0f).forEach(f -> {
            SearchIndexModel model = new SearchIndexModel();
            model.f = f;
            model.save();
        });

        Stream.of("a","c","b").forEach(f -> {
            SearchOverlapModel model2 = new SearchOverlapModel();
            model2.f = f;
            model2.save();
        });
        SearchOverlapModelIndex = true;

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size all", fooResult, hasSize(3));
        assertThat("check 0 and 1 order",  fooResult.get(0).f, lessThan(fooResult.get(1).f));
        assertThat("check 1 and 2 order", fooResult.get(1).f, lessThan(fooResult.get(2).f));

        List<SearchOverlapModel> fooResult2 = Query
                .from(SearchOverlapModel.class)
                .sortAscending("f")
                .selectAll();

        assertThat("check size all", fooResult2, hasSize(3));
        assertThat("check 0 and 1 order",  fooResult2.get(0).f, lessThan(fooResult2.get(1).f));
        assertThat("check 1 and 2 order", fooResult2.get(1).f, lessThan(fooResult2.get(2).f));

    }

    @Test
    public void testTimeout() throws Exception {
        Stream.of(new java.util.Date()).forEach(d -> {
            SearchIndexModel model = new SearchIndexModel();
            model.post_date = d;
            model.save();
        });

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .sortOldest(2.0, "post_date")
                .timeout(500.0)
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testLogin() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("loginTokens/token equalsany ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testUUIDlt() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query.from(SearchIndexModel.class)
                .where("loginTokens/token < ?", new UUID(0,0))
                .selectAll();

        assertThat("check size", fooResult, hasSize(0));
    }

    @Test
    public void testUUIDlte() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query.from(SearchIndexModel.class)
                .where("loginTokens/token <= ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }


    @Test
    public void testUUIDgt() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query.from(SearchIndexModel.class)
                .where("loginTokens/token > ?", new UUID(0,0))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testUUIDgte() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query.from(SearchIndexModel.class)
                .where("loginTokens/token >= ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesany() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchIndexModel.class)
                .where("loginTokens/token matchesany ?", new UUID(0,0))
                .selectAll();

    }

    @Test
    public void testSearchStemming() {

        SearchIndexModel model = new SearchIndexModel();
        model.one = "Managing The Learning Function";
        model.save();

        List<SearchIndexModel> s = Query.from(SearchIndexModel.class)
                .where("one matchesany ?", "manAge")
                .selectAll();
        assertThat(s, hasSize(1));

        List<SearchIndexModel> s1 = Query.from(SearchIndexModel.class)
                .where("one matchesany ?", "lEarn")
                .selectAll();
        assertThat(s1, hasSize(1));

        List<SearchIndexModel> s2 = Query.from(SearchIndexModel.class)
                .where("one matchesany ?", "functIons")
                .selectAll();
        assertThat(s2, hasSize(1));

    }

    @Test
    public void testSearchMatchAllStemming() {

        SearchIndexModel model = new SearchIndexModel();
        model.one = "Managing The Learning Function";
        model.save();

        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "The restaurant diners are at the function learning";
        model1.save();

        List<String> all = new ArrayList<>();
        all.add("function");
        all.add("diner");

        List<SearchIndexModel> s = Query.from(SearchIndexModel.class)
                .where("one matchesall ?", all)
                .selectAll();
        assertThat(s, hasSize(1));

        List<String> all1 = new ArrayList<>();
        all1.add("the");
        all1.add("learning");

        List<SearchIndexModel> s1 = Query.from(SearchIndexModel.class)
                .where("one matchesall ?", all1)
                .selectAll();
        assertThat(s1, hasSize(2));

        List<String> all2 = new ArrayList<>();
        all2.add("function");
        all2.add("learning");

        List<SearchIndexModel> s2 = Query.from(SearchIndexModel.class)
                .where("one matchesall ?", all2)
                .selectAll();
        assertThat(s2, hasSize(2));

    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesall() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchIndexModel.class)
                .where("loginTokens/token matchesall ?", new UUID(0,0))
                .selectAll();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesany2() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchIndexModel.class)
                .where("loginTokens/token matchesany ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUUIDmatchesall2() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        Query.from(SearchIndexModel.class)
                .where("loginTokens/token matchesall ?", UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298"))
                .selectAll();
    }

   @Test
    public void testComplexQuery() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.loginTokens.token = UUID.fromString("68a66f18-b668-418b-af69-8dafa6325298");
        model.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("(loginTokens/token notequalsall missing and (f equalsany missing and num equalsany missing "
                        + "and set equalsany missing and list equalsany missing and _type notequalsall ?"
                        + ") and _any matchesany '*')", UUID.fromString("68a66f18-b668-418b-af69-8dafa632529"))
                .selectAll();

        // _type notequalsall ?

        assertThat("check size", fooResult, hasSize(1));
    }

    @Test
    public void testGroupPartial() {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 20; j++) {
                SearchIndexModel model = new SearchIndexModel();
                model.one = "test " + j;
                model.save();
            }
        }

        PaginatedResult<Grouping<SearchIndexModel>> groupBy =
                Query.from(SearchIndexModel.class).groupByPartial(0L, 2, "one");
        for (Grouping<SearchIndexModel> grouping : groupBy.getItems()) {
            List<Object> cycleKeys = grouping.getKeys();
            String name = (String) cycleKeys.get(0);
            long count = grouping.getCount();
            assertThat( count, is(4L));
        }

        assertEquals(20, groupBy.getCount());
    }

    @Test
    public void testGroupOrder() {
        for (int i = 1; i <= 4; i++) {
            for (int j = 0; j < i*2; j++) {
                SearchIndexModel model = new SearchIndexModel();
                model.one = "test " + i;
                model.save();
            }
        }

        // from highest to smallest count
        long count = 4;
        List<Grouping<SearchIndexModel>> groupBy =
                Query.from(SearchIndexModel.class).groupBy("one");
        for (Grouping<SearchIndexModel> grouping : groupBy) {
            List<Object> cycleKeys = grouping.getKeys();
            String name = (String) cycleKeys.get(0);
            assertThat( grouping.getCount(), is(count*2));
            count = count - 1;
        }

        assertThat(groupBy, hasSize(4));
    }

    @Test
    public void testGroupDateandOne() {
        Date begin = new Date();
        for (int i = 1; i <= 4; i++) {
            Date post_date = DateUtils.addSeconds(begin, +1);
            for (int j = 0; j < i*2; j++) {
                SearchIndexModel model = new SearchIndexModel();
                model.one = "test " + i;
                model.post_date = post_date;
                model.save();
            }
        }

        // date first, so it will be lowest to highest count
        long count = 1;
        List<Grouping<SearchIndexModel>> groupBy =
                Query.from(SearchIndexModel.class).groupBy("post_date", "one");
        for (Grouping<SearchIndexModel> grouping : groupBy) {
            List<Object> cycleKeys = grouping.getKeys();
            String date = cycleKeys.get(0).toString();
            String one = (String) grouping.getKeys().get(1);
            assertThat( grouping.getCount(), is(count*2));
            assertThat(one, is("test " + count));
            count = count + 1;
        }

        assertThat(groupBy, hasSize(4));
    }

    @Test
    public void testQuery() throws Exception {

        SearchIndexModel model = new SearchIndexModel();
        model.one = "test";
        model.save();
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "test";
        model1.save();

        Query<SearchIndexModel> query = Query.from(SearchIndexModel.class);

        List<SearchIndexModel> selectAll = query.selectAll();
        assertThat("check selectAll", selectAll, hasSize(2));

        PaginatedResult<SearchIndexModel> select = query.select(0, 10);
        assertEquals(2, select.getCount());

        SearchIndexModel first = query.first();
        assertThat("check size", first, notNullValue());

        Iterable<SearchIndexModel> iter = query.iterable(10);
        int i = 0;
        for (SearchIndexModel s : iter) {
            i++;
        }
        assertThat("check iter", i, is(2));

        List<Grouping<SearchIndexModel>> groupBy = Query.from(SearchIndexModel.class).groupBy("one");
        Iterator<Grouping<SearchIndexModel>> iGroup = groupBy.iterator();

        while(iGroup.hasNext()) {
            Grouping element = iGroup.next();
            assertEquals(2, element.getCount());
        }

        Query<SearchIndexModel> searchQuery = Query.from(SearchIndexModel.class).where("one = ?", "test");

        List<SearchIndexModel> search = searchQuery.selectAll();
        assertThat("check where", search, hasSize(2));

        ObjectType type = ObjectType.getInstance(SearchIndexModel.class);

        List<Object> search2 = Query.fromType(type).selectAll();
        assertThat("check fromType", search2, hasSize(2));
    }

    @Test
    public void testGlobals() {
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "test";
        model1.save();

        List<SearchIndexModel> query = Query.from(SearchIndexModel.class).selectAll();

        Database defaultDatabase = Database.Static.getDefault();
        DatabaseEnvironment environment = defaultDatabase.getEnvironment();
        List<ObjectField> globalFields = environment.getFields();
        assertThat(globalFields.isEmpty(), is(false));
    }

    @Test
    public void testModification() {
        Date begin = new java.util.Date();
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "test";
        model1.as(TestModification.class).setUpdateDate(begin);
        model1.save();

        List<SearchIndexModel> sem = Query.from(SearchIndexModel.class).selectAll();
        assertThat("check testModification", sem, hasSize(1));

        List<SearchIndexModel> sem2 = Query.from(SearchIndexModel.class)
                .where("cms.content.updateDate > ?", DateUtils.addHours(new java.util.Date(), -1))
                .sortAscending("cms.content.updateDate")
                .selectAll();
        assertThat("check testModification", sem2, hasSize(1));

        List<SearchIndexModel> sem3 = Query.from(SearchIndexModel.class)
                .where("cms.content.updateDate = ?", begin)
                .sortAscending("cms.content.updateDate")
                .selectAll();
        assertThat("check testModification", sem2, hasSize(1));
    }

    @Test
    public void testMatchesAll() {
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "test headline story";
        model1.save();

        SearchIndexModel model2 = new SearchIndexModel();
        model2.one = "another story";
        model2.save();

        List<SearchIndexModel> sem = Query.from(SearchIndexModel.class).where("one matchesany ?", "headline").selectAll();
        assertThat("check matchesany", sem, hasSize(1));

        List<SearchIndexModel> sem1 = Query.from(SearchIndexModel.class).where("one matchesall ?", "headline").selectAll();
        assertThat("check matchesall", sem1, hasSize(1));

        List<String> many = new ArrayList<>();
        many.add("test");
        many.add("headline");
        List<SearchIndexModel> sem2 = Query.from(SearchIndexModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem2, hasSize(1));
        List<SearchIndexModel> sem3 = Query.from(SearchIndexModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem3, hasSize(1));

        List<String> many2 = new ArrayList<>();
        many.add("test");
        many.add("story");
        List<SearchIndexModel> sem4 = Query.from(SearchIndexModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem4, hasSize(2));
        List<SearchIndexModel> sem5 = Query.from(SearchIndexModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem5, hasSize(1));
    }

    @Test
    public void testMatchesAllCase() {
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "TeSt HeAdLiNe StOrY";
        model1.save();

        SearchIndexModel model2 = new SearchIndexModel();
        model2.one = "AnOtHeR StOrY";
        model2.save();

        List<SearchIndexModel> zeroAny = Query.from(SearchIndexModel.class).where("one matchesany ?", (Object) null).selectAll();
        assertThat("check matchesany", zeroAny, hasSize(0));

        List<SearchIndexModel> zeroAll = Query.from(SearchIndexModel.class).where("one matchesany ?", (Object) null).selectAll();
        assertThat("check matchesany", zeroAll, hasSize(0));

        List<SearchIndexModel> sem = Query.from(SearchIndexModel.class).where("one matchesany ?", "headline").selectAll();
        assertThat("check matchesany", sem, hasSize(1));

        List<SearchIndexModel> sem1 = Query.from(SearchIndexModel.class).where("one matchesall ?", "headline").selectAll();
        assertThat("check matchesall", sem1, hasSize(1));

        List<SearchIndexModel> contains = Query.from(SearchIndexModel.class).where("one contains ?", "headline").selectAll();
        assertThat("check matchesall", contains, hasSize(1));

        List<String> many = new ArrayList<>();
        many.add("test");
        many.add("headline");
        List<SearchIndexModel> sem2 = Query.from(SearchIndexModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem2, hasSize(1));
        List<SearchIndexModel> sem3 = Query.from(SearchIndexModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem3, hasSize(1));

        List<String> many2 = new ArrayList<>();
        many.add("test");
        many.add("story");
        List<SearchIndexModel> sem4 = Query.from(SearchIndexModel.class).where("one matchesany ?", many).selectAll();
        assertThat("check matchesany", sem4, hasSize(2));
        List<SearchIndexModel> sem5 = Query.from(SearchIndexModel.class).where("one matchesall ?", many).selectAll();
        assertThat("check matchesall", sem5, hasSize(1));
    }

    @Test
    public void testSortNumber() {

        SearchIndexModel model1 = new SearchIndexModel();
        model1.f = 1.0f;
        model1.d = 1.0d;
        model1.num = 100;
        model1.l = 100L;
        model1.shortType = (short) 5;
        model1.save();

        SearchIndexModel model2 = new SearchIndexModel();
        model2.f = 1.1f;
        model2.d = 1.1d;
        model2.num = 200;
        model2.l = 200L;
        model2.shortType = (short) 6;
        model2.save();

        SearchIndexModel model3 = new SearchIndexModel();
        model3.save();

        List<SearchIndexModel> floatNumber = Query.from(SearchIndexModel.class).sortAscending("f").selectAll();
        assertThat("check sort float", floatNumber, hasSize(3));
        assertThat("check 0 and 1 order",  floatNumber.get(0).f, lessThan(floatNumber.get(1).f));
        assertThat(floatNumber.get(2).f, nullValue());

        List<SearchIndexModel> doubleNumber = Query.from(SearchIndexModel.class).sortAscending("d").selectAll();
        assertThat("check sort double", doubleNumber, hasSize(3));
        assertThat("check 0 and 1 order",  doubleNumber.get(0).d, lessThan(doubleNumber.get(1).d));
        assertThat(doubleNumber.get(2).d, nullValue());

        List<SearchIndexModel> intNumber = Query.from(SearchIndexModel.class).sortAscending("num").selectAll();
        assertThat("check sort int", intNumber, hasSize(3));
        assertThat("check 0 and 1 order",  intNumber.get(0).num, lessThan(intNumber.get(1).num));
        assertThat(intNumber.get(2).num, nullValue());

        List<SearchIndexModel> longNumber = Query.from(SearchIndexModel.class).sortAscending("l").selectAll();
        assertThat("check sort long", longNumber, hasSize(3));
        assertThat("check 0 and 1 order",  longNumber.get(0).l, lessThan(longNumber.get(1).l));
        assertThat(longNumber.get(2).l, nullValue());

        List<SearchIndexModel> shortNumber = Query.from(SearchIndexModel.class).sortAscending("shortType").selectAll();
        assertThat("check sort short", shortNumber, hasSize(3));
        assertThat("check 0 and 1 order",  shortNumber.get(0).shortType, lessThan(shortNumber.get(1).shortType));
        assertThat(shortNumber.get(2).shortType, nullValue());
    }

    @Test
    public void testSortTypeFieldAll() {
        SearchIndexModel model1 = new SearchIndexModel();
        model1.one = "test headline story";
        model1.save();

        SearchIndexModel model2 = new SearchIndexModel();
        model2.one = "another story";
        model2.save();

        String sort = ObjectType.getInstance(SearchIndexModel.class).getInternalName() + "/one";

        List<SearchIndexModel> sem = Query.from(SearchIndexModel.class).sortAscending(sort).selectAll();
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

        SearchIndexModel model = new SearchIndexModel();
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
                hasItems("com.psddev.dari.test.MethodIndexElasticModel/getName",
                         "com.psddev.dari.test.MethodIndexElasticModel/getFoo",
                         "com.psddev.dari.test.MethodIndexElasticModel/taggable.getFoo2",
                         "com.psddev.dari.test.MethodIndexElasticModel/getInfo",
                         "com.psddev.dari.test.MethodIndexElasticModel/getNameFirstLetter",
                         "com.psddev.dari.test.MethodIndexElasticModel/getPrefixName"));
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
    public void testPainless() {
        ElasticsearchDatabase db = (ElasticsearchDatabase) Database.Static.getDefault();
        assertThat(db.isModuleInstalled("lang-painless", "org.elasticsearch.painless.PainlessPlugin"), is(true));
    }

    @Test
    public void testDenormalizedTags() {
        IndexTag t = new IndexTag();
        t.setName("pizza");
        t.save();

        IndexTag nt = new IndexTag();
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

    @Test
    public void testScoreNormalizedScore()  {
        SearchIndexModel search = new SearchIndexModel();
        search.eid = "939393";
        search.name = "Bill";
        search.message = "tough";
        search.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("eid matches ?", "939393")
                .selectAll();

        assertThat(fooResult, hasSize(1));

        assertThat(fooResult.get(0).getState().getExtras().size(), is(4));

        Float score = ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score, is(lessThan(.3f)));

        Float normalizedScore =  ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore, is (1.0f));
    }

    @Test
    public void testStringNormalizedScore()  {
        SearchIndexModel search = new SearchIndexModel();
        search.eid = "939393";
        search.name = "Bill Rick Smith";
        search.message = "tough";
        search.save();

        SearchIndexModel search1 = new SearchIndexModel();
        search1.eid = "939394";
        search1.name = "Bill Joseph";
        search1.message = "easy";
        search1.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("name matches ?", "Bill")
                .selectAll();

        assertThat(fooResult, hasSize(2));

        assertThat(fooResult.get(0).getState().getExtras().size(), is(4));

        Float score = ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score, is(lessThan(.3f)));
        Float normalizedScore =  ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore, is (1.0f));

        Float score1 = ObjectUtils.to(Float.class, fooResult.get(1).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score1, is(lessThan(score)));
        Float normalizedScore1 =  ObjectUtils.to(Float.class, fooResult.get(1).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore1, is (lessThan(1.0f)));
        assertThat(normalizedScore1, is (lessThan(normalizedScore)));
    }

    @Test
    public void testLargeField() {
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            msg = msg.append("a");
        }
        String message = msg.toString();

        SearchIndexModel search = new SearchIndexModel();
        search.eid = "939393";
        search.name = "Bill";
        search.message = message;
        search.save();

        List<SearchIndexModel> fooResult = Query
                .from(SearchIndexModel.class)
                .where("eid matches ?", "939393")
                .selectAll();
        assertThat(fooResult.get(0).getMessage().length(), is(equalTo(2000)));
        List<SearchIndexModel> fooResult1 = Query
                .from(SearchIndexModel.class)
                .where("message = ?", message.substring(0,ElasticsearchDatabase.MAX_BINARY_FIELD_LENGTH))
                .selectAll();
        assertThat(fooResult1, hasSize(1));
        List<SearchIndexModel> fooResult2 = Query
                .from(SearchIndexModel.class)
                .where("message = ?", message.substring(0,ElasticsearchDatabase.MAX_BINARY_FIELD_LENGTH+1))
                .selectAll();
        assertThat(fooResult2, hasSize(0));
    }
}

