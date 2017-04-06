package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Database;
import com.psddev.dari.db.PredicateParser;
import com.psddev.dari.db.Query;
import com.psddev.dari.test.SearchIndexModel;
import com.psddev.dari.util.CollectionUtils;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.node.Node;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class ElasticInitializationTest {

    private ElasticsearchDatabase database;
    private Map<String, Object> settings;

    @Before
    public void before() {
        database = new ElasticsearchDatabase();
        settings = new HashMap<>();
    }

    @After
    public void deleteModels() {
        Query.from(SearchIndexModel.class).deleteAllImmediately();
    }

    private void put(String path, Object value) {
        CollectionUtils.putByPath(settings, path, value);
    }

    @Test
    public void embeddedElastic() {
        String nodeHost = "http://localhost:9200/";
        assertThat(ElasticsearchDatabase.checkAlive(nodeHost), is(true));

        String elasticCluster = ElasticsearchDatabase.getClusterName(nodeHost);
        assertThat(elasticCluster, is(notNullValue()));
        if (ElasticDBSuite.ElasticTests.getIsEmbedded()) {
            Node node = EmbeddedElasticsearchServer.getNode();
            assertThat(node, is(notNullValue()));
        }

        Settings.setOverride(ElasticsearchDatabase.DEFAULT_DATABASE_NAME, ElasticsearchDatabase.DATABASE_NAME);

        put(ElasticsearchDatabase.DEFAULT_DATABASE_NAME, ElasticsearchDatabase.DATABASE_NAME);
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING + "class", ElasticsearchDatabase.class.getName());
        put(ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, elasticCluster);
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING, "index1");
        put("1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "9300");
        put("1/" + ElasticsearchDatabase.CLUSTER_REST_PORT_SUB_SETTING, "9200");
        put("1/" + ElasticsearchDatabase.HOSTNAME_SUB_SETTING, "localhost");
        put(ElasticsearchDatabase.SUBQUERY_RESOLVE_LIMIT_SETTING, "1000");
        database.initialize("", settings);
        assertThat(database.getIndexName(), is("index1"));
        assertThat(database.getClusterName(), is(elasticCluster));
        assertThat(database.getClusterNodes(), hasSize(1));
        List<ElasticsearchNode> n = database.getClusterNodes();
        assertThat(n.get(0).getHostname(), is("localhost"));
        assertThat(n.get(0).getPort(), is(9300));
        assertThat(n.get(0).getRestPort(), is(9200));
    }

    @Test(expected = NullPointerException.class)
    public void testMissingSettings() {
        put(ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, "foo");
        database.initialize("", settings);
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
    public void testPainless() {
        ElasticsearchDatabase db = Database.Static.getFirst(ElasticsearchDatabase.class);
        assertThat(db.isModuleInstalled("lang-painless", "org.elasticsearch.painless.PainlessPlugin"), Matchers.is(true));
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

        if (Database.Static.getDefault().getName().equals(ElasticsearchDatabase.DATABASE_NAME)) {
            assertThat(fooResult.get(0).getState().getExtras().size(), Matchers.is(4));
        }

        Float score = ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score, Matchers.is(lessThan(.3f)));

        Float normalizedScore =  ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore, Matchers.is (1.0f));
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
        assertThat("max length", fooResult.get(0).getMessage().length(), Matchers.is(equalTo(2000)));
        List<SearchIndexModel> fooResult1 = Query
                .from(SearchIndexModel.class)
                .where("message startswith ?", message.substring(0, 255))
                .selectAll();
        assertThat("check database size", fooResult1, hasSize(1));
        List<SearchIndexModel> fooResult2 = Query
                .from(SearchIndexModel.class)
                .where("message startswith ?", message.substring(0, 256))
                .selectAll();
        assertThat("after the limit", fooResult2, hasSize(0));
    }

    @Test
    public void testPredicates() {
        SearchIndexModel s = new SearchIndexModel();
        s.setOne("3");
        s.setD(3d);
        s.save();

        ElasticsearchDatabase e = Database.Static.getFirst(ElasticsearchDatabase.class);
        Query q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.EQUALS_ANY_OPERATOR + " ?", "3");
        QueryBuilder x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.NOT_EQUALS_ALL_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.LESS_THAN_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.LESS_THAN_OR_EQUALS_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.GREATER_THAN_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.GREATER_THAN_OR_EQUALS_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.STARTS_WITH_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.CONTAINS_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.MATCHES_ANY_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.MATCHES_ALL_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains(ElasticsearchDatabase.ANY_FIELD));
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

        assertThat(fooResult.get(0).getState().getExtras().size(), Matchers.is(4));

        Float score = ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score, Matchers.is(lessThan(.3f)));
        Float normalizedScore =  ObjectUtils.to(Float.class, fooResult.get(0).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore, Matchers.is (1.0f));

        Float score1 = ObjectUtils.to(Float.class, fooResult.get(1).getExtra(ElasticsearchDatabase.SCORE_EXTRA));
        assertThat(score1, Matchers.is(lessThan(score)));
        Float normalizedScore1 =  ObjectUtils.to(Float.class, fooResult.get(1).getExtra(ElasticsearchDatabase.NORMALIZED_SCORE_EXTRA));
        assertThat(normalizedScore1, Matchers.is (lessThan(1.0f)));
        assertThat(normalizedScore1, Matchers.is (lessThan(normalizedScore)));
    }

    @Test
    public void testExclude() {
        ElasticModel search = new ElasticModel();
        search.name = "Mickey Mouse";
        search.desc = "Indexed but not in Any";
        search.save();

        List<ElasticModel> fooResult = Query
                .from(ElasticModel.class)
                .where("name matches ?", "Mouse")
                .selectAll();
        assertThat(fooResult, hasSize(1));

        List<ElasticModel> fooResult1 = Query
                .from(ElasticModel.class)
                .where("_any matches ?", "Mouse")
                .selectAll();
        assertThat(fooResult1, hasSize(1));

        List<ElasticModel> fooResult2 = Query
                .from(ElasticModel.class)
                .where("desc matches ?", "Indexed")
                .selectAll();
        assertThat(fooResult2, hasSize(1));

        List<ElasticModel> fooResult3 = Query
                .from(ElasticModel.class)
                .where("_any matches ?", "Indexed")
                .selectAll();
        assertThat(fooResult3, hasSize(0));
    }
}
