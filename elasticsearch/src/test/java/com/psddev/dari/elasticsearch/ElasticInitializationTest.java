package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.AbstractDatabase;
import com.psddev.dari.db.AsyncDatabaseReader;
import com.psddev.dari.db.AsyncDatabaseWriter;
import com.psddev.dari.db.BulkDebugServlet;
import com.psddev.dari.db.Database;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Predicate;
import com.psddev.dari.db.PredicateParser;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Record;
import com.psddev.dari.db.SqlDatabase;
import com.psddev.dari.db.WriteOperation;
import com.psddev.dari.test.SearchIndexModel;
import com.psddev.dari.util.AsyncQueue;
import com.psddev.dari.util.CollectionUtils;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.Task;
import com.psddev.dari.util.TaskExecutor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.node.Node;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class ElasticInitializationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticInitializationTest.class);

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

        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING, "index1");
        put(ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, elasticCluster);
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING+ "class", ElasticsearchDatabase.class.getName());
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "9300");
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "localhost");

        database.initialize("", settings);
    }

    @Test
    public void testBulk() {

        String name = Settings.getOrError(String.class, Database.DEFAULT_DATABASE_SETTING, "No default database!");

        LOGGER.debug("Name: {}", name);

        for (int i = 0; i < 50; i++) {
            SearchIndexModel m = new SearchIndexModel();
            m.setOne("Testing " + i);
            m.save();
        }

        Database source = Database.Static.getInstance("elasticsearch");
        Database destination = Database.Static.getInstance("elasticsearch");

        String executor = BulkDebugServlet.COPIER_PREFIX + " from " + source + " to " + destination;
        AsyncQueue<Object> queue = new AsyncQueue<>();
        Query<Object> query = Query
                .fromType(null)
                .resolveToReferenceOnly();

        if (destination instanceof AbstractDatabase) {
            Set<String> destinationGroups = ((AbstractDatabase<?>) destination).getGroups();

            if (!destinationGroups.contains(UUID.randomUUID().toString())) {
                Set<UUID> unsavableTypeIds = source.getEnvironment()
                        .getTypes()
                        .stream()
                        .map(Record::getId)
                        .collect(Collectors.toSet());

                for (ObjectType type : source.getEnvironment().getTypes()) {
                    if (type.getObjectClass() == null
                            || type.getGroups().contains(Modification.class.getName())
                            || type.isAbstract()
                            || type.isEmbedded()) {
                        unsavableTypeIds.remove(type.getId());
                    } else {
                        for (String typeGroup : type.getGroups()) {
                            if (destinationGroups.contains(typeGroup)) {
                                unsavableTypeIds.remove(type.getId());
                                break;
                            }
                        }
                    }
                }

                query.and("_type != ?", unsavableTypeIds);
            }
        }

        query.getOptions().put(SqlDatabase.USE_JDBC_FETCH_SIZE_QUERY_OPTION, false);

        boolean deleteDestination = false;

        if (deleteDestination) {
            destination.deleteByQuery(query);
        }

        (new AsyncDatabaseReader<Object>(
                executor, queue, source, query) {
            @Override
            protected Object produce() {
                Object obj = super.produce();
                if (obj instanceof Record) {
                    this.setProgress(this.getProgress() + " (last: " + ((Record) obj).getId() + ")");
                }
                return obj;
            }
        }).submit();

        queue.closeAutomatically();

        int writersCount = 5;
        int commitSize = 200;

        long maximumDataLength = Runtime.getRuntime().freeMemory() / 10 / writersCount / commitSize;

        LOGGER.debug("maximum data length: " + maximumDataLength);

        for (int i = 0; i < writersCount; ++ i) {
            AsyncDatabaseWriter<Object> writer = new AsyncDatabaseWriter<>(
                    executor, queue, destination, WriteOperation.SAVE_UNSAFELY, commitSize, true);

            writer.setCommitSizeJitter(0.2);
            writer.setMaximumDataLength(maximumDataLength);
            writer.submit();
        }

        List<TaskExecutor> copyExecutors = new ArrayList<>();
        for (TaskExecutor executor1 : TaskExecutor.Static.getAll()) {
            if (executor1.getName().startsWith(BulkDebugServlet.COPIER_PREFIX)) {
                copyExecutors.add(executor1);
            }
        }

        boolean done = false;
        while (!done) {
            for (TaskExecutor executor2 : copyExecutors) {
                LOGGER.debug("name: {} term: {} shut: {}", executor2.getName(), executor2.isTerminated() ? "true":"false",
                        executor2.isShutdown() ? "true":"false");
                if (executor2.isTerminated() || executor2.isShutdown()) {
                    done = true;
                } else {
                    List<Object> tasks = executor2.getTasks();
                    for (Object taskObject : tasks) {
                        if (!(taskObject instanceof Task)) {
                            continue;
                        }

                        Task task = (Task) taskObject;
                        LOGGER.info("Progress: {} {} {}", executor2.getName(), task.getName(), task.getProgress());
                        if (!task.isRunning()) {
                            done = true;
                        }
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                LOGGER.warn("Sleep threw exception");
            }
        }
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
        ElasticsearchDatabase db = (ElasticsearchDatabase) Database.Static.getDefault();
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

        ElasticsearchDatabase e = (ElasticsearchDatabase) Database.Static.getDefault();
        Query q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.EQUALS_ANY_OPERATOR + " ?", "3");
        QueryBuilder x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.NOT_EQUALS_ALL_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.LESS_THAN_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.LESS_THAN_OR_EQUALS_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.GREATER_THAN_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.GREATER_THAN_OR_EQUALS_OPERATOR + " ?", 3d);
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.STARTS_WITH_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.CONTAINS_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.MATCHES_ANY_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

        q = Query.from(SearchIndexModel.class).using(e).where("_any " + PredicateParser.MATCHES_ALL_OPERATOR + " ?", "3");
        x = e.predicateToQueryBuilder(q.getPredicate(), q);
        Assert.assertTrue(x.toString().contains("_all"));

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
}
