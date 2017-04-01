package com.psddev.dari.elasticsearch;

import com.psddev.dari.test.LocationIndexTest;
import com.psddev.dari.test.ModificationDenormalizedTest;
import com.psddev.dari.test.ModificationEmbeddedTest;
import com.psddev.dari.test.NumberIndexTest;
import com.psddev.dari.test.ReadTest;
import com.psddev.dari.test.RegionCircleIndexTest;
import com.psddev.dari.test.RegionIndexTest;
import com.psddev.dari.test.RegionLocationTest;
import com.psddev.dari.test.SearchArticleIndexTest;
import com.psddev.dari.test.SearchIndexTest;
import com.psddev.dari.test.SingletonTest;
import com.psddev.dari.test.StateIndexTest;
import com.psddev.dari.test.StringIndexTest;
import com.psddev.dari.test.TypeIndexTest;
import com.psddev.dari.test.UuidIndexTest;
import com.psddev.dari.test.WriteTest;
import com.psddev.dari.util.Settings;
import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        ElasticDBSuite.ElasticTests.class
})
public class ElasticDBSuite {
    public static class ElasticTests {

        private static final Logger LOGGER = LoggerFactory.getLogger(ElasticTests.class);
        private static boolean isEmbedded = false;

        public static boolean getIsEmbedded() {
            return isEmbedded;
        }

        public static void setIsEmbedded(boolean isEmbedded) {
            ElasticTests.isEmbedded = isEmbedded;
        }

        /**
         *
         */
        public static void deleteIndex(String index, String nodeHost) throws IOException {
            LOGGER.info("Deleting Index " + index);
            try {
                CloseableHttpClient httpClient = HttpClients.createDefault();
                HttpDelete delete = new HttpDelete(nodeHost + index);
                delete.addHeader("accept", "application/json");
                CloseableHttpResponse response = httpClient.execute(delete);
                try {
                    HttpEntity entity = response.getEntity();
                    String res = EntityUtils.toString(entity);
                    EntityUtils.consume(entity);
                    LOGGER.info("Deleted Index {} [{}] {}", index, res, response.getStatusLine().getStatusCode());
                } finally {
                    response.close();
                }
            } catch (Exception error) {
                LOGGER.warn(
                        String.format("Warning: deleteIndex[%s: %s]",
                                error.getClass().getName(),
                                error.getMessage()),
                        error);
                throw error;
            }
        }

        public static void ElasticSetupDatabase() throws Exception {

            LOGGER.info("ElasticSetupDatabase");

            String clusterName = "elasticsearch_a";

            Settings.setOverride(ElasticsearchDatabase.DEFAULT_DATABASE_NAME, ElasticsearchDatabase.DATABASE_NAME);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "class", ElasticsearchDatabase.class.getName());
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, clusterName);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING, "index1");
            //Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.DEFAULT_DATAFIELD_TYPE_SETTING, ElasticsearchDatabase.RAW_DATAFIELD_TYPE);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.DEFAULT_DATAFIELD_TYPE_SETTING, ElasticsearchDatabase.JSON_DATAFIELD_TYPE);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.DATA_TYPE_RAW_SETTING, "-* +com.psddev.dari.test.WriteModel ");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "9300");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "1/" + ElasticsearchDatabase.CLUSTER_REST_PORT_SUB_SETTING, "9200");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + "1/" + ElasticsearchDatabase.HOSTNAME_SUB_SETTING, "localhost");
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.SUBQUERY_RESOLVE_LIMIT_SETTING, "1000");

            String nodeHost = ElasticsearchDatabase.getNodeHost("localhost", "9200");
            if (!ElasticsearchDatabase.checkAlive(nodeHost)) {
                LOGGER.info("Starting Embedded");
                // ok create embedded since it is not already running for test
                isEmbedded = true;
                EmbeddedElasticsearchServer.deleteDataDirectory();
                EmbeddedElasticsearchServer.setup(clusterName);
            } else {
                LOGGER.info("Already running");
            }
            String verifyClusterName = ElasticsearchDatabase.getClusterName(nodeHost);
            Settings.setOverride(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, verifyClusterName);
            deleteIndex(Settings.get(ElasticsearchDatabase.SETTING_KEY_PREFIX + ElasticsearchDatabase.INDEX_NAME_SUB_SETTING) + "*", nodeHost);
        }

        public static TestSuite suite() throws Exception {
            LOGGER.info("Starting Elastic test");
            ElasticSetupDatabase();
            TestSuite suite = new TestSuite();
            suite.addTest(new JUnit4TestAdapter(ElasticDatabaseConnectionTest.class));
            suite.addTest(new JUnit4TestAdapter(ElasticInitializationTest.class));
            suite.addTest(new JUnit4TestAdapter(LocationIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(ModificationDenormalizedTest.class));
            suite.addTest(new JUnit4TestAdapter(ModificationEmbeddedTest.class));
            suite.addTest(new JUnit4TestAdapter(NumberIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(ReadTest.class));
            suite.addTest(new JUnit4TestAdapter(RegionCircleIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(RegionIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(RegionLocationTest.class));
            suite.addTest(new JUnit4TestAdapter(SearchArticleIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(SearchIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(SingletonTest.class));
            suite.addTest(new JUnit4TestAdapter(StateIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(StringIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(TypeIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(UuidIndexTest.class));
            suite.addTest(new JUnit4TestAdapter(WriteTest.class));
            return suite;
        }

    }

}
